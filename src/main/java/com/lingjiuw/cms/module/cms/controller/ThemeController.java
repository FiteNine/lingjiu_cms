package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.ThemeVO;
import com.lingjiuw.cms.module.cms.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cms/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:theme:list')")
    public Result<List<ThemeVO>> list() {
        return Result.ok(themeService.list());
    }
}
