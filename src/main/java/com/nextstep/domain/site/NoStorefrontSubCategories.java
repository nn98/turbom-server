package com.nextstep.domain.site;

import java.util.Set;

/**
 * (category, subCategory)별 "층/호 정보 없음" 비율이 90% 이상인 업종 — 통신판매업 등 물리적
 * 점포 없이 신고 가능한 업종. 90% 임계값을 기계적으로 적용한 결과(2026-07-18, 실측 91,772건
 * 기준), 수작업 큐레이션 아님. 표본이 작은 항목(n≤5건: 물류창고업체·박물관 및 미술관·
 * 비디오물감상실업·비디오물배급업·온라인음악서비스제공업·일반야영장업·대규모점포·썰매장업·
 * 용기냉동기특정설비·가축분뇨수집운반업·배출가스전문정비사업자(확인검사대행자)·제재업)은
 * 통계적으로 약한 신호 — 향후 실사례로 오분류가 확인되면 개별 조정.
 *
 * 판정은 이 클래스 단독으로 끝나지 않는다 — 호출부(TenancyQueryService)가 같은 businessName의
 * 다른 레코드에 물리적 신호(무점포 목록 밖 업종이거나 실제 층/호 정보)가 있으면 이 클래스의
 * 판정을 무시하고 매장으로 취급한다(동물병원 더 하임처럼 한 상호가 매장업종+무점포업종 라이선스를
 * 동시에 보유하는 경우 대응).
 */
public final class NoStorefrontSubCategories {

    private record CategoryPair(String category, String subCategory) {
    }

    private static final Set<CategoryPair> NO_STOREFRONT = Set.of(
        new CategoryPair("건강", "의료기기판매(임대)업"),
        new CategoryPair("기타", "물류창고업체"),
        new CategoryPair("기타", "민방위급수시설"),
        new CategoryPair("기타", "옥외광고업"),
        new CategoryPair("기타", "인쇄사"),
        new CategoryPair("기타", "출판사"),
        new CategoryPair("동물", "동물미용업"),
        new CategoryPair("동물", "동물생산업"),
        new CategoryPair("동물", "동물용의료용구판매업"),
        new CategoryPair("동물", "동물운송업"),
        new CategoryPair("동물", "동물위탁관리업"),
        new CategoryPair("동물", "동물전시업"),
        new CategoryPair("동물", "동물판매업"),
        new CategoryPair("문화", "게임물배급업"),
        new CategoryPair("문화", "게임물제작업"),
        new CategoryPair("문화", "대중문화예술기획업"),
        new CategoryPair("문화", "박물관 및 미술관"),
        new CategoryPair("문화", "비디오물감상실업"),
        new CategoryPair("문화", "비디오물배급업"),
        new CategoryPair("문화", "비디오물제작업"),
        new CategoryPair("문화", "숙박업"),
        new CategoryPair("문화", "영화배급업"),
        new CategoryPair("문화", "영화수입업"),
        new CategoryPair("문화", "영화제작업"),
        new CategoryPair("문화", "온라인음악서비스제공업"),
        new CategoryPair("문화", "음반및음악영상물배급업"),
        new CategoryPair("문화", "음반및음악영상물제작업"),
        new CategoryPair("문화", "일반야영장업"),
        new CategoryPair("생활", "대규모점포"),
        new CategoryPair("생활", "방문판매업"),
        new CategoryPair("생활", "썰매장업"),
        new CategoryPair("생활", "전화권유판매업"),
        new CategoryPair("생활", "통신판매업"),
        new CategoryPair("생활", "후원방문판매업체"),
        new CategoryPair("식품", "건강기능식품유통전문판매업"),
        new CategoryPair("식품", "건강기능식품일반판매업"),
        new CategoryPair("식품", "식품운반업"),
        new CategoryPair("식품", "용기냉동기특정설비"),
        new CategoryPair("식품", "축산물운반업"),
        new CategoryPair("자원환경", "가축분뇨수집운반업"),
        new CategoryPair("자원환경", "고압가스업"),
        new CategoryPair("자원환경", "대기오염물질배출시설설치사업장"),
        new CategoryPair("자원환경", "목재수입유통업"),
        new CategoryPair("자원환경", "배출가스전문정비사업자(확인검사대행자)"),
        new CategoryPair("자원환경", "저수조청소업"),
        new CategoryPair("자원환경", "제재업"),
        new CategoryPair("자원환경", "특정고압가스업")
    );

    private NoStorefrontSubCategories() {
    }

    public static boolean isNoStorefront(String category, String subCategory) {
        if (category == null || subCategory == null) return false;
        return NO_STOREFRONT.contains(new CategoryPair(category, subCategory));
    }
}
