package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.StatsVO;
import com.lingjiuw.cms.module.cms.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cms/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public Result<StatsVO> stats() {
        return Result.ok(statsService.stats());
    }
}
