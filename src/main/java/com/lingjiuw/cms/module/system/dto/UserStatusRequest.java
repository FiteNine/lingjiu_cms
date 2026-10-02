package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 修改用户状态请求 */
public record UserStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1") Integer status) {
}
