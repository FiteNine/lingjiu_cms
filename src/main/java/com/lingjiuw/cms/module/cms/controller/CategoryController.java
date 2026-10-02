package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.CategoryNode;
import com.lingjiuw.cms.module.cms.dto.CategorySaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.service.CategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cms/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('cms:category:list')")
    public Result<List<CategoryNode>> tree() {
        return Result.ok(categoryService.tree());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cms:category:list')")
    public Result<List<CmsCategory>> list() {
        return Result.ok(categoryService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:category:add')")
    @OperLog(module = "分类管理", action = "新增分类")
    public Result<Void> create(@RequestBody @Valid CategorySaveRequest request) {
        categoryService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:category:edit')")
    @OperLog(module = "分类管理", action = "编辑分类")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CategorySaveRequest request) {
        categoryService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:category:delete')")
    @OperLog(module = "分类管理", action = "删除分类")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
