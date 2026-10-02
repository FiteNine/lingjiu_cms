package com.lingjiuw.cms.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 服务商连通性测试请求 */
public record ProviderTestRequest(
        @NotBlank(message = "请选择测试用的模型") @Size(max = 64, message = "模型名最长 64 字符") String model) {
}
