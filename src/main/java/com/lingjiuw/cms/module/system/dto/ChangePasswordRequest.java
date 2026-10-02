package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改密码请求 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间") String newPassword) {

    /** 明文密码不随 toString 进入日志/异常信息 */
    @Override
    public String toString() {
        return "ChangePasswordRequest[oldPassword=***, newPassword=***]";
    }
}
