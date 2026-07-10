import os
import sys
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pytest
import requests

from sangga_client import SanggaApiClient, SanggaApiError, SanggaStoreItem


def _mock_response(body: dict, status: int = 200):
    resp = MagicMock()
    resp.status_code = status
    resp.raise_for_status = MagicMock()
    if status >= 400:
        resp.raise_for_status.side_effect = requests.HTTPError(f"HTTP {status}")
    resp.json.return_value = {"header": {"resultCode": "00"}, "body": body}
    return resp


def test_fetch_dong_stores_paginates_until_total_count_reached():
    page1 = {
        "totalCount": 3, "pageNo": 1, "numOfRows": 2,
        "items": [
            {"bizesId": "1", "bizesNm": "가게1", "lnoAdr": "경기도 성남시 수정구 금토동 405-1",
             "lnoCd": "4113310300104050001", "flrNo": "1", "hoNo": "101", "lon": "127.1", "lat": "37.4"},
            {"bizesId": "2", "bizesNm": "가게2", "lnoAdr": "경기도 성남시 수정구 금토동 405-1",
             "lnoCd": "4113310300104050001", "flrNo": "1", "hoNo": "102", "lon": "127.1", "lat": "37.4"},
        ],
    }
    page2 = {
        "totalCount": 3, "pageNo": 2, "numOfRows": 2,
        "items": [
            {"bizesId": "3", "bizesNm": "가게3", "lnoAdr": "경기도 성남시 수정구 금토동 405-3",
             "lnoCd": "4113310300104050003", "flrNo": "1", "hoNo": "", "lon": "127.2", "lat": "37.5"},
        ],
    }

    session = MagicMock()
    session.get.side_effect = [_mock_response(page1), _mock_response(page2)]

    client = SanggaApiClient(service_key="dummy-key", session=session)
    items = client.fetch_dong_stores(adong_code="4113355000", page_size=2)

    assert len(items) == 3
    assert session.get.call_count == 2
    assert all(isinstance(i, SanggaStoreItem) for i in items)
    assert items[0].bizesNm == "가게1"
    assert items[2].lnoCd == "4113310300104050003"

    # 두 번째 호출의 pageNo가 실제로 2로 넘어갔는지 확인
    second_call_params = session.get.call_args_list[1].kwargs["params"]
    assert second_call_params["pageNo"] == 2
    assert second_call_params["divId"] == "adongCd"
    assert second_call_params["key"] == "4113355000"


def test_fetch_dong_stores_stops_on_empty_page_even_if_total_count_wrong():
    page1 = {"totalCount": 100, "pageNo": 1, "numOfRows": 1, "items": [
        {"bizesId": "1", "bizesNm": "가게1", "lnoAdr": "경기도 성남시 수정구 금토동 405-1",
         "lnoCd": "4113310300104050001"},
    ]}
    page2_empty = {"totalCount": 100, "pageNo": 2, "numOfRows": 1, "items": []}

    session = MagicMock()
    session.get.side_effect = [_mock_response(page1), _mock_response(page2_empty)]

    client = SanggaApiClient(service_key="dummy-key", session=session)
    items = client.fetch_dong_stores(adong_code="4113355000", page_size=1)

    assert len(items) == 1  # totalCount(100)를 믿지 않고 빈 페이지에서 멈춤


def test_client_rejects_empty_service_key():
    with pytest.raises(ValueError):
        SanggaApiClient(service_key="")


def test_request_retries_then_raises_after_max_retry():
    session = MagicMock()
    session.get.side_effect = requests.ConnectionError("network down")

    client = SanggaApiClient(service_key="dummy-key", session=session)
    with patch("sangga_client.time.sleep"):  # 테스트에서 실제로 대기하지 않음
        with pytest.raises(SanggaApiError):
            client.fetch_dong_stores(adong_code="4113355000")

    assert session.get.call_count == 3  # MAX_RETRY


def test_request_raises_on_missing_body():
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json.return_value = {"header": {"resultCode": "99", "resultMsg": "ERROR"}}
    session = MagicMock()
    session.get.return_value = resp

    client = SanggaApiClient(service_key="dummy-key", session=session)
    with pytest.raises(SanggaApiError):
        client.fetch_dong_stores(adong_code="4113355000")


def test_fetch_radius_same_category_count_uses_server_side_filter():
    """storeListInRadius를 정확히 호출하고, storeZoneInRadius(다른 오퍼레이션)와
    혼동하지 않는지, indsSclsCd 서버 필터가 실제로 넘어가는지 확인."""
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json.return_value = {
        "header": {"resultCode": "00"},
        "body": {"totalCount": 14, "pageNo": 1, "numOfRows": 1, "items": []},
    }
    session = MagicMock()
    session.get.return_value = resp

    client = SanggaApiClient(service_key="dummy-key", session=session)
    count = client.fetch_radius_same_category_count(
        cx=127.1045, cy=37.4012, industry_small_code="I20102", radius_meters=300,
    )

    assert count == 14
    call_url = session.get.call_args.args[0]
    assert call_url.endswith("/storeListInRadius")
    assert "storeZoneInRadius" not in call_url  # 이름 비슷한 다른 오퍼레이션과 혼동 방지

    params = session.get.call_args.kwargs["params"]
    assert params["indsSclsCd"] == "I20102"
    assert params["radius"] == 300
    assert params["cx"] == 127.1045 and params["cy"] == 37.4012


def test_fetch_radius_rejects_radius_over_max():
    client = SanggaApiClient(service_key="dummy-key", session=MagicMock())
    with pytest.raises(ValueError):
        client.fetch_radius_same_category_count(
            cx=127.1, cy=37.4, industry_small_code="I20102", radius_meters=2001,
        )


def test_fetch_radius_returns_zero_when_no_match():
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json.return_value = {
        "header": {"resultCode": "00"},
        "body": {"totalCount": 0, "pageNo": 1, "numOfRows": 1, "items": []},
    }
    session = MagicMock()
    session.get.return_value = resp

    client = SanggaApiClient(service_key="dummy-key", session=session)
    count = client.fetch_radius_same_category_count(
        cx=127.1, cy=37.4, industry_small_code="I99999",
    )
    assert count == 0
