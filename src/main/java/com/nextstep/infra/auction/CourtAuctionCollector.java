package com.nextstep.infra.auction;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.nextstep.domain.auction.AuctionCase;
import com.nextstep.domain.auction.AuctionCaseRef;
import java.util.ArrayList;
import java.util.List;

public class CourtAuctionCollector {

    private static final String SEARCH_URL =
        "https://www.courtauction.go.kr/pgj/index.on?w2xPath=/pgj/ui/pgj100/PGJ157M00.xml";

    private final AuctionListParser listParser = new AuctionListParser();
    private final AuctionDetailParser detailParser = new AuctionDetailParser();

    public List<AuctionCase> collectSeongnamSujeongGu() {
        List<AuctionCase> results = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            String listText = search(page);
            List<AuctionCaseRef> refs = listParser.parse(listText);

            // Each result row's "소재지 및 내역" cell is a real <a onclick="moveDtlPage(N)">
            // link keyed by row index (not by case number or the row-selection checkbox
            // labeled "선택", which was the original — incorrect — click target). Clicking it
            // is an AJAX content swap with no real browser navigation (href="#"), so
            // page.goBack() never returns to the list — re-run the search instead of
            // navigating back for every case after the first.
            for (int i = 0; i < refs.size(); i++) {
                if (i > 0) {
                    search(page);
                }
                AuctionCaseRef ref = refs.get(i);
                page.click("a[onclick=\"moveDtlPage(" + i + ")\"]");
                // Every grid on this site carries an invisible (screen-reader-only) <caption>
                // that lists all of its column labels as plain text — so a plain text=
                // selector for any field label matches that hidden caption FIRST and hangs
                // forever polling an element that can never become visible. Target the last
                // match instead, which lands on the real, rendered field.
                page.locator("text=청구금액").last().waitFor();
                // The grid widget renders its column labels/captions immediately but fills in
                // cell values via a separate, slightly-delayed data-binding pass — waiting for
                // the "청구금액" label alone still races the actual numbers. No further
                // text-based signal distinguishes "labels rendered" from "values populated", so
                // settle on a fixed delay (empirically enough for this framework).
                page.waitForTimeout(2000);
                String detailText = page.innerText("body");
                results.add(detailParser.parse(ref.caseNumber(), ref.itemNumber(), detailText));
            }

            browser.close();
        }

        return results;
    }

    /**
     * Navigates to the search screen and runs the 경기도/성남시 수정구/건물/상업용및업무용
     * search, returning the rendered results list text.
     */
    private String search(Page page) {
        page.navigate(SEARCH_URL);
        page.waitForLoadState();
        page.getByText("소재지(지번주소)").click();
        page.waitForTimeout(1000);

        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("시/도"))
            .selectOption("경기도");
        page.waitForTimeout(500);

        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("시/군/구"))
            .first()
            .selectOption("성남시 수정구");
        page.waitForTimeout(500);

        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("대분류"))
            .selectOption("건물");
        page.waitForTimeout(500);

        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("중분류"))
            .selectOption("상업용및업무용");
        page.waitForTimeout(500);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("검색").setExact(true))
            .click();
        // This site is an AJAX-driven SPA (no real navigation on search — the URL never
        // changes), so waitForLoadState() resolves immediately against the page's original
        // load and never actually waits for the results to render. Wait for the results
        // header text instead (see the caption note above for why .last() matters here too).
        page.locator("text=총 물건수").last().waitFor();

        return page.innerText("body");
    }
}
