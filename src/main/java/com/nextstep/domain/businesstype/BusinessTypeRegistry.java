package com.nextstep.domain.businesstype;

import java.util.HashMap;
import java.util.Map;

public class BusinessTypeRegistry {

    private static final StandardBusinessType DEFAULT =
        new StandardBusinessType(LocationCertainty.LOCATED, RelatedTypeKeys.none(), false);

    private final Map<BusinessTypeKey, StandardBusinessType> byKey = new HashMap<>();

    public BusinessTypeRegistry() {
        registerNoPhysicalStoreCategories();
        registerPeerBasedReliability("기타", "담배소매업");
        registerPair(new BusinessTypeKey("식품", "집단급식소"), new BusinessTypeKey("식품", "위탁급식영업"));
    }

    public BusinessType lookup(String category, String subCategory) {
        if (category == null || subCategory == null) return DEFAULT;
        return byKey.getOrDefault(new BusinessTypeKey(category, subCategory), DEFAULT);
    }

    public BusinessType lookup(BusinessTypeKey key) {
        return byKey.getOrDefault(key, DEFAULT);
    }

    private void registerNoPhysicalStore(String category, String subCategory) {
        replace(new BusinessTypeKey(category, subCategory), existing ->
            new StandardBusinessType(LocationCertainty.NO_PHYSICAL_STORE, existing.relatedTypeKeys(),
                existing.peerBasedReliability()));
    }

    private void registerPeerBasedReliability(String category, String subCategory) {
        replace(new BusinessTypeKey(category, subCategory), existing ->
            new StandardBusinessType(existing.locationCertainty(), existing.relatedTypeKeys(), true));
    }

    private void registerPair(BusinessTypeKey a, BusinessTypeKey b) {
        replace(a, existing -> withRelated(existing, b));
        replace(b, existing -> withRelated(existing, a));
    }

    private StandardBusinessType withRelated(StandardBusinessType existing, BusinessTypeKey partner) {
        java.util.Set<BusinessTypeKey> merged = new java.util.HashSet<>(existing.relatedTypeKeys().values());
        merged.add(partner);
        return new StandardBusinessType(existing.locationCertainty(), new RelatedTypeKeys(merged),
            existing.peerBasedReliability());
    }

    private void replace(BusinessTypeKey key, java.util.function.UnaryOperator<StandardBusinessType> update) {
        StandardBusinessType current = byKey.getOrDefault(key, DEFAULT);
        byKey.put(key, update.apply(current));
    }

    private void registerNoPhysicalStoreCategories() {
        // NoStorefrontSubCategories.NO_STOREFRONT의 47개 항목을 그대로 이관 — 원본 파일과
        // 한 줄씩 대조해서 옮길 것(순서·값 무관, 존재 여부만 일치해야 함)
        registerNoPhysicalStore("건강", "의료기기판매(임대)업");
        registerNoPhysicalStore("기타", "물류창고업체");
        registerNoPhysicalStore("기타", "민방위급수시설");
        registerNoPhysicalStore("기타", "옥외광고업");
        registerNoPhysicalStore("기타", "인쇄사");
        registerNoPhysicalStore("기타", "출판사");
        registerNoPhysicalStore("동물", "동물미용업");
        registerNoPhysicalStore("동물", "동물생산업");
        registerNoPhysicalStore("동물", "동물용의료용구판매업");
        registerNoPhysicalStore("동물", "동물운송업");
        registerNoPhysicalStore("동물", "동물위탁관리업");
        registerNoPhysicalStore("동물", "동물전시업");
        registerNoPhysicalStore("동물", "동물판매업");
        registerNoPhysicalStore("문화", "게임물배급업");
        registerNoPhysicalStore("문화", "게임물제작업");
        registerNoPhysicalStore("문화", "대중문화예술기획업");
        registerNoPhysicalStore("문화", "박물관 및 미술관");
        registerNoPhysicalStore("문화", "비디오물감상실업");
        registerNoPhysicalStore("문화", "비디오물배급업");
        registerNoPhysicalStore("문화", "비디오물제작업");
        registerNoPhysicalStore("문화", "숙박업");
        registerNoPhysicalStore("문화", "영화배급업");
        registerNoPhysicalStore("문화", "영화수입업");
        registerNoPhysicalStore("문화", "영화제작업");
        registerNoPhysicalStore("문화", "온라인음악서비스제공업");
        registerNoPhysicalStore("문화", "음반및음악영상물배급업");
        registerNoPhysicalStore("문화", "음반및음악영상물제작업");
        registerNoPhysicalStore("문화", "일반야영장업");
        registerNoPhysicalStore("생활", "대규모점포");
        registerNoPhysicalStore("생활", "방문판매업");
        registerNoPhysicalStore("생활", "썰매장업");
        registerNoPhysicalStore("생활", "전화권유판매업");
        registerNoPhysicalStore("생활", "통신판매업");
        registerNoPhysicalStore("생활", "후원방문판매업체");
        registerNoPhysicalStore("식품", "건강기능식품유통전문판매업");
        registerNoPhysicalStore("식품", "건강기능식품일반판매업");
        registerNoPhysicalStore("식품", "식품운반업");
        registerNoPhysicalStore("식품", "용기냉동기특정설비");
        registerNoPhysicalStore("식품", "축산물운반업");
        registerNoPhysicalStore("자원환경", "가축분뇨수집운반업");
        registerNoPhysicalStore("자원환경", "고압가스업");
        registerNoPhysicalStore("자원환경", "대기오염물질배출시설설치사업장");
        registerNoPhysicalStore("자원환경", "목재수입유통업");
        registerNoPhysicalStore("자원환경", "배출가스전문정비사업자(확인검사대행자)");
        registerNoPhysicalStore("자원환경", "저수조청소업");
        registerNoPhysicalStore("자원환경", "제재업");
        registerNoPhysicalStore("자원환경", "특정고압가스업");
    }
}
