package com.nextstep.web;

import com.nextstep.application.SiteQueryService;
import com.nextstep.web.dto.ApiDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SiteController {

    private final SiteQueryService siteQueryService;

    public SiteController(SiteQueryService siteQueryService) {
        this.siteQueryService = siteQueryService;
    }

    @GetMapping("/sites/search")
    public SearchResponse search(@RequestParam(required = false) String query) {
        return siteQueryService.search(query);
    }

    @GetMapping("/sites/{pnu}")
    public SiteDetailResponse siteDetail(@PathVariable String pnu) {
        return siteQueryService.getSiteDetail(pnu);
    }

    @GetMapping("/units/{unitId}")
    public UnitDetailResponse unitDetail(@PathVariable String unitId) {
        return siteQueryService.getUnitDetail(unitId);
    }
}
