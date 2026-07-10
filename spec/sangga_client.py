"""
소상공인시장진흥공단 상가(상권)정보 API 클라이언트.

핵심 판단:
- 조회는 '동 단위로 묶어서' 한다 (storeListInDong, divId=adongCd).
  인허가 레코드마다 PNU로 개별 호출하면 개발계정 일 1,000건 쿼터가 즉시 소진됨.
  동 단위 조회는 페이지네이션(numOfRows/pageNo)으로 몇 번이면 끝남.
- 조인(merge.py)은 반드시 정확한 PNU로 한다. 이 클라이언트는 '조회'만 책임지고
  '매칭'은 책임지지 않는다 — 책임 분리.

확정(공식 활용가이드 hwp 원문 확인, `상권조회-API-명세.md` 참조):
- BASE_URL은 /sdsc2/ 가 맞다(이전 TODO였던 /sdsc vs /sdsc2 논쟁 해소).
- 오퍼레이션명 함정 주의: "반경내 상권조회"(storeZoneInRadius, #2)는 상권 폴리곤
  경계 데이터를 주는 별개 오퍼레이션이다. 우리가 쓰는 건 "반경내 상가업소조회"
  (storeListInRadius, #10) — 이름이 비슷해도 응답 스키마가 완전히 다르다.
- 반경조회는 indsSclsCd 파라미터로 서버 측 업종 필터링을 지원한다. 클라이언트에서
  전체를 받아 필터링할 필요 없이, 필터된 응답의 totalCount를 그대로 쓰면 된다
  (fetch_radius_same_category_count 참조).
- divId=adongCd 에 들어갈 '행정동코드'는 PNU 앞 10자리인 '법정동코드'와 다를 수 있다.
  실제 값은 여전히 별도 확인 필요(TODO 유지).
"""
from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

import requests

DEFAULT_BASE_URL = "https://apis.data.go.kr/B553077/api/open/sdsc2"
DEFAULT_PAGE_SIZE = 1000  # API 문서 기준 1회 최대 1000건
DEFAULT_RADIUS_METERS = 300
MAX_RADIUS_METERS = 2000  # 공식 문서 기준 상한
REQUEST_TIMEOUT_SEC = 10
MAX_RETRY = 3
RETRY_BACKOFF_SEC = 1.0


class SanggaApiError(Exception):
    pass


@dataclass
class SanggaStoreItem:
    """API 응답 1건. 필드명은 실제 응답 스키마(bizesId 등) 그대로 유지한다."""
    bizesId: str
    bizesNm: str
    indsLclsNm: str | None
    indsMclsNm: str | None
    indsSclsNm: str | None
    ksicNm: str | None
    lnoCd: str | None      # 19자리 지번코드 = 우리 도메인의 PNU와 동일 개념
    lnoAdr: str | None     # 지번주소
    rdnmAdr: str | None    # 도로명주소
    bldMngNo: str | None   # 건물관리번호
    dongNo: str | None     # 동정보(공동주택 등)
    flrNo: str | None      # 층정보
    hoNo: str | None       # 호정보
    lon: float | None
    lat: float | None
    chgGb: str | None      # C(생성)/U(수정)/D(삭제)
    chgDt: str | None

    @staticmethod
    def from_raw(raw: dict[str, Any]) -> "SanggaStoreItem":
        def f(key: str) -> Any:
            return raw.get(key)

        lon = raw.get("lon")
        lat = raw.get("lat")
        return SanggaStoreItem(
            bizesId=f("bizesId"),
            bizesNm=f("bizesNm"),
            indsLclsNm=f("indsLclsNm"),
            indsMclsNm=f("indsMclsNm"),
            indsSclsNm=f("indsSclsNm"),
            ksicNm=f("ksicNm"),
            lnoCd=f("lnoCd"),
            lnoAdr=f("lnoAdr"),
            rdnmAdr=f("rdnmAdr"),
            bldMngNo=f("bldMngNo"),
            dongNo=f("dongNo"),
            flrNo=f("flrNo"),
            hoNo=f("hoNo"),
            lon=float(lon) if lon not in (None, "") else None,
            lat=float(lat) if lat not in (None, "") else None,
            chgGb=f("chgGb"),
            chgDt=f("chgDt"),
        )


class SanggaApiClient:
    """행정동 단위 상가업소 조회 클라이언트. 실 네트워크 호출은 이 클래스에서만 발생한다."""

    def __init__(
        self,
        service_key: str,
        base_url: str = DEFAULT_BASE_URL,
        session: requests.Session | None = None,
    ) -> None:
        if not service_key:
            raise ValueError("service_key가 비어 있습니다.")
        self._service_key = service_key
        self._base_url = base_url.rstrip("/")
        self._session = session or requests.Session()

    def fetch_dong_stores(
        self,
        adong_code: str,
        industry_large_code: str | None = None,
        page_size: int = DEFAULT_PAGE_SIZE,
    ) -> list[SanggaStoreItem]:
        """행정동 전체를 페이지네이션으로 끝까지 끌어온다. '묶어서 조회'의 실제 구현."""
        items: list[SanggaStoreItem] = []
        page_no = 1
        total_count: int | None = None

        while True:
            body = self._request(
                "storeListInDong",
                {
                    "divId": "adongCd",
                    "key": adong_code,
                    "numOfRows": page_size,
                    "pageNo": page_no,
                    "type": "json",
                    **({"indsLclsCd": industry_large_code} if industry_large_code else {}),
                },
            )
            if total_count is None:
                total_count = body.get("totalCount", 0)

            page_items = body.get("items", [])
            items.extend(SanggaStoreItem.from_raw(raw) for raw in page_items)

            if not page_items or len(items) >= total_count:
                break
            page_no += 1

        return items

    def fetch_radius_same_category_count(
        self,
        cx: float,
        cy: float,
        industry_small_code: str,
        radius_meters: int = DEFAULT_RADIUS_METERS,
    ) -> int:
        """반경 내 동일 업종(소분류) 점포 수. marketInfo.sameCategoryNearbyCount 산출용.

        오퍼레이션 #10 반경내 상가업소조회(storeListInRadius) — #2 반경내
        상권조회(storeZoneInRadius, 폴리곤 데이터)와 이름이 비슷하니 혼동 금지.

        indsSclsCd로 서버 측 업종 필터링이 되므로, 클라이언트에서 items를 받아
        하나씩 업종을 비교할 필요가 없다. 필터된 응답의 totalCount를 그대로 쓰면
        된다 — numOfRows=1로 최소 페이로드만 요청.
        """
        if radius_meters > MAX_RADIUS_METERS:
            raise ValueError(f"반경은 최대 {MAX_RADIUS_METERS}m까지다: {radius_meters}")

        body = self._request(
            "storeListInRadius",
            {
                "cx": cx,
                "cy": cy,
                "radius": radius_meters,
                "indsSclsCd": industry_small_code,
                "numOfRows": 1,  # totalCount만 필요, item 본문은 안 씀
                "pageNo": 1,
                "type": "json",
            },
        )
        return body.get("totalCount", 0)

    def _request(self, operation: str, extra_params: dict[str, Any]) -> dict[str, Any]:
        """공통 GET+재시도. operation은 'storeListInDong' | 'storeListInRadius' 등."""
        params = {"serviceKey": self._service_key, **extra_params}
        url = f"{self._base_url}/{operation}"

        last_error: Exception | None = None
        for attempt in range(1, MAX_RETRY + 1):
            try:
                resp = self._session.get(url, params=params, timeout=REQUEST_TIMEOUT_SEC)
                resp.raise_for_status()
                data = resp.json()
                body = data.get("body")
                if body is None:
                    raise SanggaApiError(f"응답에 body가 없습니다: {data}")
                return body
            except (requests.RequestException, ValueError) as exc:
                last_error = exc
                if attempt < MAX_RETRY:
                    time.sleep(RETRY_BACKOFF_SEC * attempt)
                    continue
        raise SanggaApiError(f"{MAX_RETRY}회 재시도 후 실패: {last_error}") from last_error
