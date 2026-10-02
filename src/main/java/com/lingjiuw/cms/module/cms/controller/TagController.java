package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.TagSaveRequest;
import com.lingjiuw.cms.module.cms.dto.TagVO;
import com.lingjiuw.cms.module.cms.service.TagService;
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

import java.util.List;

@RestController
@RequestMapping("/api/cms/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:tag:list')")
    public Result<List<TagVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(tagService.list(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:tag:add')")
    @OperLog(module = "标签管理", action = "新增标签")
    public Result<Void> create(@RequestBody @Valid TagSaveRequest request) {
        tagService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:tag:edit')")
    @OperLog(module = "标签管理", action = "编辑标签")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid TagSaveRequest request) {
        tagService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:tag:delete')")
    @OperLog(module = "标签管理", action = "删除标签")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.ok();
    }
}
