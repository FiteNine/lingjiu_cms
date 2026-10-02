package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cms/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:media:list')")
    public Result<PageResult<CmsMedia>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(mediaService.page(page, size, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:media:upload')")
    @OperLog(module = "媒体库", action = "上传文件")
    public Result<CmsMedia> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(mediaService.upload(file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:media:delete')")
    @OperLog(module = "媒体库", action = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return Result.ok();
    }
}
