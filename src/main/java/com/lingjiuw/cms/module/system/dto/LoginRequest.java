package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64") String username,
        @NotBlank(message = "密码不能为空") String password) {

    /** 明文密码不随 toString 进入日志/异常信息 */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
