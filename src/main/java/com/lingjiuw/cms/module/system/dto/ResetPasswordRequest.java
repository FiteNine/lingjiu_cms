package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置用户密码请求 */
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间") String password) {
}
