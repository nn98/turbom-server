package com.nextstep.infra.auction;

import com.nextstep.domain.auction.AuctionCase;
import com.nextstep.domain.auction.AuctionScheduleEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuctionDetailParser {

    private static final Pattern MINIMUM_PRICE_BLOCK = Pattern.compile(
        "최저매각가격\\s*\\n\\(매수신청보증금\\)\\s*\\n([\\d,]+)원\\s*\\n\\(([\\d,]+)원\\)"
    );
    private static final Pattern JIBUN_ADDRESS = Pattern.compile("목록\\d+ 소재지\\s*\\n(.+?)\\s*\\n");
    private static final Pattern SCHEDULE_ROW = Pattern.compile(
        "(?m)^(\\d{4}\\.\\d{2}\\.\\d{2}) \\((\\d{2}:\\d{2})\\) (매각기일|매각결정기일) (.+?) ([\\d,]+)원(?:\\s+(\\S+))?$"
    );

    public AuctionCase parse(String caseNumber, int itemNumber, String detailPageText) {
        String[] courtParts = splitCourt(firstLineAfter(detailPageText, "담당"));
        BigDecimal[] priceAndDeposit = parseMinimumPriceAndDeposit(detailPageText);

        return new AuctionCase(
            caseNumber,
            itemNumber,
            courtParts[0],
            courtParts[1],
            firstLineAfter(detailPageText, "물건종류"),
            extractJibunAddress(detailPageText),
            parseKrwAmount(firstLineAfter(detailPageText, "감정평가액")),
            priceAndDeposit[0],
            priceAndDeposit[1],
            firstLineAfter(detailPageText, "입찰방법"),
            firstLineAfter(detailPageText, "예정매각기일"),
            firstLineAfter(detailPageText, "사건접수"),
            firstLineAfter(detailPageText, "경매개시일"),
            firstLineAfter(detailPageText, "배당요구종기"),
            parseKrwAmount(firstLineAfter(detailPageText, "청구금액")),
            extractAppraisalSummary(detailPageText),
            parseScheduleHistory(detailPageText)
        );
    }

    private String[] splitCourt(String value) {
        if (value == null) {
            return new String[] {null, null};
        }
        String[] parts = value.split("\\|", 2);
        String court = parts[0].trim();
        String division = parts.length > 1 ? parts[1].trim() : null;
        return new String[] {court, division};
    }

    private String extractJibunAddress(String text) {
        Matcher matcher = JIBUN_ADDRESS.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private BigDecimal[] parseMinimumPriceAndDeposit(String text) {
        Matcher matcher = MINIMUM_PRICE_BLOCK.matcher(text);
        if (!matcher.find()) {
            return new BigDecimal[] {null, null};
        }
        return new BigDecimal[] {parseAmount(matcher.group(1)), parseAmount(matcher.group(2))};
    }

    private String extractAppraisalSummary(String text) {
        int start = text.indexOf("감정평가요항표 요약");
        int end = text.indexOf("인근매각물건사례");
        if (start == -1 || end == -1 || end <= start) {
            return null;
        }
        return text.substring(start, end).trim();
    }

    private List<AuctionScheduleEntry> parseScheduleHistory(String text) {
        List<AuctionScheduleEntry> entries = new ArrayList<>();
        Matcher matcher = SCHEDULE_ROW.matcher(text);
        while (matcher.find()) {
            entries.add(new AuctionScheduleEntry(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4).trim(),
                parseAmount(matcher.group(5)),
                matcher.group(6)
            ));
        }
        return entries;
    }

    private String firstLineAfter(String text, String label) {
        Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(label) + "\\s*$\\n+(.+)$");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private BigDecimal parseKrwAmount(String value) {
        if (value == null) {
            return null;
        }
        return parseAmount(value.replace("원", ""));
    }

    private BigDecimal parseAmount(String digitsWithCommas) {
        if (digitsWithCommas == null || digitsWithCommas.isBlank()) {
            return null;
        }
        return new BigDecimal(digitsWithCommas.replace(",", "").trim());
    }
}
