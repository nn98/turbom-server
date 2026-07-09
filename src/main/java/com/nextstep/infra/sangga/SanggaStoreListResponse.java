package com.nextstep.infra.sangga;

import java.util.List;

public record SanggaStoreListResponse(SanggaHeader header, SanggaBody body) {

    public record SanggaHeader(String resultCode, String resultMsg) {
    }

    public record SanggaBody(List<SanggaStoreItem> items, int numOfRows, int pageNo, int totalCount) {
    }

    public record SanggaStoreItem(String indsSclsNm) {
    }
}
