import pytest
from jibun_pnu import load_legaldong_codes, parse_pnu

CODES = {
    "경기도 성남시 수정구 태평동": "4113110100",
    "경기도 성남시 수정구 수진동": "4113110300",
    "경기도 성남시 수정구 복정동": "4113110900",
    "경기도 성남시 분당구 정자동": "4113510500",
    "경기도 성남시 중원구 금광동": "4113310200",
}


def make_pnu(dong_code: str, san: bool, bon: int, bu: int) -> str:
    return dong_code + ("1" if san else "0") + f"{bon:04d}" + f"{bu:04d}"


def test_정상_지번_번지():
    pnu = parse_pnu("경기도 성남시수정구 태평동 2254", CODES)
    assert pnu == make_pnu("4113110100", False, 2254, 0)


def test_정상_지번_번지_본번_정확히():
    pnu = parse_pnu("경기도 성남시 수정구 수진동 4224번지", CODES)
    assert pnu == make_pnu("4113110300", False, 4224, 0)


def test_구식_지번_호_표기는_번지와_동일하게_파싱():
    pnu_ho = parse_pnu("경기도 성남시수정구 태평동 4253호   ", CODES)
    pnu_beonji = parse_pnu("경기도 성남시 수정구 태평동 4253번지", CODES)
    assert pnu_ho == pnu_beonji
    assert pnu_ho == make_pnu("4113110100", False, 4253, 0)


def test_본번_부번_조합():
    pnu = parse_pnu("경기도 성남시수정구 수진동 2679-20호   ", CODES)
    assert pnu == make_pnu("4113110300", False, 2679, 20)


def test_산_표기_인식():
    pnu = parse_pnu("경기도 성남시수정구 복정동 산65-1외35필지호", CODES)
    assert pnu == make_pnu("4113110900", True, 65, 1)


def test_번지_뒤_건물명_있어도_파싱():
    pnu = parse_pnu("경기도 성남시 중원구 금광동 3465번지 백산하이츠", CODES)
    assert pnu == make_pnu("4113310200", False, 3465, 0)


COLLISION_CODES = {
    **CODES,
    "경기도 성남시 중원구 갈현동": "4113310300",
    "서울특별시 은평구 갈현동": "1138510500",
}


def test_동일한_짧은동이름이_두_도시에_있어도_구_이름으로_구분한다():
    # 실사례(2026-07-18 서울 스코프 확장 검증): "갈현동"이 성남시 중원구와
    # 서울 은평구에 둘 다 있음. 짧은 동이름만 보고 매칭하면 구를 무시한 채
    # 먼저 걸리는 쪽으로 잘못 붙는다 - 상위 구/시 이름도 주소에 있어야 인정.
    seoul_pnu = parse_pnu("서울특별시 은평구 갈현동 50", COLLISION_CODES)
    seongnam_pnu = parse_pnu("경기도 성남시 중원구 갈현동 50", COLLISION_CODES)

    assert seoul_pnu == make_pnu("1138510500", False, 50, 0)
    assert seongnam_pnu == make_pnu("4113310300", False, 50, 0)
    assert seoul_pnu != seongnam_pnu


def test_본번이_4자리를_넘으면_실패():
    # 실사례: "경기도 성남시 수정구 복정동 67814 101호" - 원본에 하이픈이 빠져
    # "678-14"가 "67814"로 붙어버린 오기(誤記). 4자리를 넘는 본/부번은 PNU
    # 스펙(각 4자리) 위반이라 파싱 실패로 처리해야 함(19자 초과 PNU 생성 방지).
    assert parse_pnu("경기도 성남시수정구 복정동 67814 101호", CODES) is None


def test_동_이름_뒤_숫자_없으면_실패():
    assert parse_pnu("경기도 성남시수정구 복정동 남성대군인아파트호", CODES) is None


def test_동_이름_자체가_없으면_실패():
    assert parse_pnu("호", CODES) is None


def test_알수없는_동은_실패():
    assert parse_pnu("경기도 성남시 수정구 없는동 123", CODES) is None


def test_공백_주소는_실패():
    assert parse_pnu("", CODES) is None
    assert parse_pnu("   ", CODES) is None


def test_load_legaldong_codes_존재만_포함():
    codes = load_legaldong_codes("legaldong_codes.csv")
    assert codes["경기도 성남시 수정구 창곡동"] == "4113110800"
    # 폐지된 코드(구 성남시 창곡동, 구 조회)는 포함되지 않아야 함
    assert "경기도 성남시 창곡동" not in codes
