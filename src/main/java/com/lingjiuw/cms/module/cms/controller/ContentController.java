package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.ContentQueryRequest;
import com.lingjiuw.cms.module.cms.dto.ContentSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentStatusRequest;
import com.lingjiuw.cms.module.cms.dto.ContentVO;
import com.lingjiuw.cms.module.cms.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用内容 {@code cms_content} 的后台接口（static-publish.md §2.3–§2.5）。
 *
 * <p>这里是全部内容的唯一入口（含内置的 article 类型），也是静态发布引擎的取数来源；
 * 保存时会在同一事务里同步维护 {@code cms_content_index} / {@code cms_content_category} /
 * {@code cms_content_tag} 三张派生表（见 {@link ContentService}）。
 */
@RestController
@RequestMapping("/api/cms/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:content:list')")
    public Result<PageResult<ContentVO>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String typeCode,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(contentService.page(new ContentQueryRequest(page, size, typeCode, status, keyword)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:content:list')")
    public Result<ContentVO> detail(@PathVariable Long id) {
        return Result.ok(contentService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:content:add')")
    @OperLog(module = "内容管理", action = "新增内容")
    public Result<Void> create(@RequestBody @Valid ContentSaveRequest request) {
        contentService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:content:edit')")
    @OperLog(module = "内容管理", action = "编辑内容")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ContentSaveRequest request) {
        contentService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:content:delete')")
    @OperLog(module = "内容管理", action = "删除内容")
    public Result<Void> delete(@PathVariable Long id) {
        contentService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('cms:content:publish')")
    @OperLog(module = "内容管理", action = "修改内容状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid ContentStatusRequest request) {
        contentService.updateStatus(id, request.status());
        return Result.ok();
    }
}
