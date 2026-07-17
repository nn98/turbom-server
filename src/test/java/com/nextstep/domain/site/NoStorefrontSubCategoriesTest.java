package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NoStorefrontSubCategoriesTest {

    @Test
    void 통신판매업은_무점포로_분류된다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", "통신판매업")).isTrue();
    }

    @Test
    void 방문판매업은_무점포로_분류된다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", "방문판매업")).isTrue();
    }

    @Test
    void 일반음식점은_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("식품", "일반음식점")).isFalse();
    }

    @Test
    void 담배소매업은_무점포가_아니다() {
        // 84.5%로 90% 임계값 미만 — 편의점 등에 붙는 부가허가라 실제 매장 있음(기존 도메인 지식과 일치)
        assertThat(NoStorefrontSubCategories.isNoStorefront("기타", "담배소매업")).isFalse();
    }

    @Test
    void 동물병원은_무점포가_아니다() {
        // 동물병원(위생/시설 실사 필요)과 동물미용업/동물위탁관리업(무점포 후보)은 다른 소분류
        assertThat(NoStorefrontSubCategories.isNoStorefront("동물", "동물병원")).isFalse();
    }

    @Test
    void 알수없는_조합은_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("없는카테고리", "없는소분류")).isFalse();
    }

    @Test
    void category나_subCategory가_null이면_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront(null, "통신판매업")).isFalse();
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", null)).isFalse();
    }
}
