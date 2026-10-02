package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.MenuItemSaveRequest;
import com.lingjiuw.cms.module.cms.dto.MenuSaveRequest;
import com.lingjiuw.cms.module.cms.dto.MenuVO;
import com.lingjiuw.cms.module.cms.service.CmsMenuService;
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

/**
 * 导航菜单（static-publish.md §2.4）：写的是站点自己的 {@code cms_menu} / {@code cms_menu_item}，
 * 与平台的 {@code /api/system/menus}（{@code sys_menu}）不是一回事。
 */
@RestController
@RequestMapping("/api/cms/menus")
@RequiredArgsConstructor
public class CmsMenuController {

    private final CmsMenuService cmsMenuService;

    /** 当前站点的菜单列表，每个菜单带菜单项树 */
    @GetMapping
    @PreAuthorize("hasAuthority('cms:menu:list')")
    public Result<List<MenuVO>> list() {
        return Result.ok(cmsMenuService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:menu:add')")
    @OperLog(module = "导航菜单", action = "新增菜单")
    public Result<Void> create(@RequestBody @Valid MenuSaveRequest request) {
        cmsMenuService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:menu:edit')")
    @OperLog(module = "导航菜单", action = "编辑菜单")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid MenuSaveRequest request) {
        cmsMenuService.update(id, request);
        return Result.ok();
    }

    /** 删菜单，连同它的菜单项一起逻辑删 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:menu:delete')")
    @OperLog(module = "导航菜单", action = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        cmsMenuService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('cms:menu:add')")
    @OperLog(module = "导航菜单", action = "新增菜单项")
    public Result<Void> createItem(@PathVariable Long id, @RequestBody @Valid MenuItemSaveRequest request) {
        cmsMenuService.createItem(id, request);
        return Result.ok();
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('cms:menu:edit')")
    @OperLog(module = "导航菜单", action = "编辑菜单项")
    public Result<Void> updateItem(@PathVariable Long itemId, @RequestBody @Valid MenuItemSaveRequest request) {
        cmsMenuService.updateItem(itemId, request);
        return Result.ok();
    }

    /** 删菜单项，连同它的子项一起逻辑删 */
    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('cms:menu:delete')")
    @OperLog(module = "导航菜单", action = "删除菜单项")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        cmsMenuService.deleteItem(itemId);
        return Result.ok();
    }
}
