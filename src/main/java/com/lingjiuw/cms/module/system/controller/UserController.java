package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.ResetPasswordRequest;
import com.lingjiuw.cms.module.system.dto.UserSaveRequest;
import com.lingjiuw.cms.module.system.dto.UserStatusRequest;
import com.lingjiuw.cms.module.system.dto.UserVO;
import com.lingjiuw.cms.module.system.service.UserService;
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

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(page, size, username, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:add')")
    @OperLog(module = "用户管理", action = "新增用户")
    public Result<Void> create(@RequestBody @Valid UserSaveRequest request) {
        userService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperLog(module = "用户管理", action = "编辑用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid UserSaveRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:delete')")
    @OperLog(module = "用户管理", action = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperLog(module = "用户管理", action = "修改用户状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid UserStatusRequest request) {
        userService.updateStatus(id, request.status());
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('sys:user:reset')")
    @OperLog(module = "用户管理", action = "重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return Result.ok();
    }
}
