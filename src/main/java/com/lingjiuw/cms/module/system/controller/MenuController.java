package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.MenuNode;
import com.lingjiuw.cms.module.system.dto.MenuSaveRequest;
import com.lingjiuw.cms.module.system.service.MenuService;
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
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public Result<List<MenuNode>> tree() {
        return Result.ok(menuService.tree());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public Result<List<MenuNode>> list() {
        return Result.ok(menuService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:menu:add')")
    @OperLog(module = "菜单管理", action = "新增菜单")
    public Result<Void> create(@RequestBody @Valid MenuSaveRequest request) {
        menuService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:edit')")
    @OperLog(module = "菜单管理", action = "编辑菜单")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid MenuSaveRequest request) {
        menuService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:delete')")
    @OperLog(module = "菜单管理", action = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }
}
