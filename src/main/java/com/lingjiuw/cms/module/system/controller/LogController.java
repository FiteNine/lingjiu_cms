package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.OperLogVO;
import com.lingjiuw.cms.module.system.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperLogService operLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:log:list')")
    public Result<PageResult<OperLogVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(required = false) String action) {
        return Result.ok(operLogService.page(page, size, username, action));
    }
}
