package com.nextstep.infra.sangga;

public record SanggaStoreListResponse(SanggaHeader header, SanggaBody body) {

    public record SanggaHeader(String resultCode, String resultMsg) {
    }

    // totalCount는 Integer(래퍼)로 둔다 - NODATA_ERROR 응답은 body가 "{}"로 비어 있어
    // 필드 자체가 없다. 원시 int였다면 결측을 못 받아 역직렬화가 깨질 수 있다.
    public record SanggaBody(Integer totalCount) {
    }
}
