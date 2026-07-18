package com.nextstep.infra.auction;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightSmokeCheck {

    @Test
    void 헤드리스_브라우저가_실행되고_페이지_타이틀을_읽는다() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><head><title>스모크 테스트</title></head><body>ok</body></html>");
            assertThat(page.title()).isEqualTo("스모크 테스트");
            browser.close();
        }
    }
}
