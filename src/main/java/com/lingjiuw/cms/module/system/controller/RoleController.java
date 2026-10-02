package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.RoleBrief;
import com.lingjiuw.cms.module.system.dto.RoleMenuRequest;
import com.lingjiuw.cms.module.system.dto.RoleSaveRequest;
import com.lingjiuw.cms.module.system.entity.SysRole;
import com.lingjiuw.cms.module.system.service.RoleService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public Result<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String name) {
        return Result.ok(roleService.page(page, size, name));
    }

    /** 角色下拉：角色管理页（sys:role:list）与用户管理页的角色选择（sys:user:list）都要用 */
    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('sys:role:list', 'sys:user:list')")
    public Result<List<RoleBrief>> all() {
        return Result.ok(roleService.all());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:add')")
    @OperLog(module = "角色管理", action = "新增角色")
    public Result<Void> create(@RequestBody @Valid RoleSaveRequest request) {
        roleService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:edit')")
    @OperLog(module = "角色管理", action = "编辑角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid RoleSaveRequest request) {
        roleService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    @OperLog(module = "角色管理", action = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('sys:role:assign')")
    public Result<Map<String, List<Long>>> menuIds(@PathVariable Long id) {
        return Result.ok(Map.of("menuIds", roleService.menuIds(id)));
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('sys:role:assign')")
    @OperLog(module = "角色管理", action = "分配菜单权限")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody @Valid RoleMenuRequest request) {
        roleService.assignMenus(id, request.menuIds());
        return Result.ok();
    }
}
