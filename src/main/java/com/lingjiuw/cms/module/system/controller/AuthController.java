package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.ChangePasswordRequest;
import com.lingjiuw.cms.module.system.dto.LoginRequest;
import com.lingjiuw.cms.module.system.dto.LoginResponse;
import com.lingjiuw.cms.module.system.dto.ProfileVO;
import com.lingjiuw.cms.module.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @OperLog(module = "登录", action = "用户登录")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 当前登录用户信息（自助接口：登录即可访问，不额外校验功能权限） */
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.ok(authService.profile());
    }

    /** 修改本人密码（自助接口：登录即可访问，不额外校验功能权限；以原密码校验身份） */
    @PutMapping("/password")
    @OperLog(module = "登录", action = "修改密码")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }

    @PostMapping("/logout")
    @OperLog(module = "登录", action = "退出登录")
    public Result<Void> logout() {
        // JWT 无状态，登出由前端丢弃 token 实现（服务端无黑名单，见 README「设计取舍」）
        return Result.ok();
    }
}
