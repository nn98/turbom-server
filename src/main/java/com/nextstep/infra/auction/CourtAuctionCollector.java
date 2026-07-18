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
            page.waitForLoadState();

            String listText = page.innerText("body");
            List<AuctionCaseRef> refs = listParser.parse(listText);

            for (AuctionCaseRef ref : refs) {
                page.getByText(ref.caseNumber() + " 선택").first().click();
                page.waitForLoadState();
                String detailText = page.innerText("body");
                results.add(detailParser.parse(ref.caseNumber(), ref.itemNumber(), detailText));
                page.goBack();
                page.waitForLoadState();
            }

            browser.close();
        }

        return results;
    }
}
