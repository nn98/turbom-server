package com.nextstep.infra.auction;

import com.nextstep.domain.auction.AuctionCaseRef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuctionListParser {

    private static final Pattern CASE_DELIMITER =
        Pattern.compile("(?m)^\\S+(\\d{4}타경\\d+) 선택$");
    private static final Pattern ITEM_LINE =
        Pattern.compile("(?m)^\\d{4}타경\\d+ (\\d+)$");

    public List<AuctionCaseRef> parse(String listPageText) {
        List<Integer> matchStarts = new ArrayList<>();
        List<Integer> matchEnds = new ArrayList<>();
        List<String> caseNumbers = new ArrayList<>();

        Matcher delimiterMatcher = CASE_DELIMITER.matcher(listPageText);
        while (delimiterMatcher.find()) {
            matchStarts.add(delimiterMatcher.start());
            matchEnds.add(delimiterMatcher.end());
            caseNumbers.add(delimiterMatcher.group(1));
        }

        List<AuctionCaseRef> refs = new ArrayList<>();
        for (int i = 0; i < matchEnds.size(); i++) {
            int blockStart = matchEnds.get(i);
            int blockEnd = (i + 1 < matchStarts.size())
                ? matchStarts.get(i + 1)
                : listPageText.length();
            String block = listPageText.substring(blockStart, blockEnd);

            Matcher itemMatcher = ITEM_LINE.matcher(block);
            if (itemMatcher.find()) {
                refs.add(new AuctionCaseRef(caseNumbers.get(i), Integer.parseInt(itemMatcher.group(1))));
            }
        }
        return refs;
    }
}
