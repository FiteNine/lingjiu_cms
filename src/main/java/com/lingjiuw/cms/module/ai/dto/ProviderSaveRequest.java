package com.lingjiuw.cms.module.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增/编辑 AI 服务商请求；apiKey 留空表示不修改（新增时表示先不填） */
public record ProviderSaveRequest(
        @NotBlank(message = "服务商名称不能为空") @Size(max = 64, message = "服务商名称最长 64 字符") String name,
        @NotBlank(message = "服务商标识不能为空")
        @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "服务商标识只能用小写字母、数字、下划线或短横线")
        String code,
        @NotBlank(message = "协议不能为空") String protocol,
        @NotBlank(message = "base_url 不能为空") @Size(max = 255, message = "base_url 最长 255 字符")
        @Pattern(regexp = "https?://.+", message = "base_url 必须是以 http:// 或 https:// 开头的地址") String baseUrl,
        @Size(max = 255, message = "API Key 最长 255 字符") String apiKey,
        @Min(value = 0, message = "status 只能是 0 或 1") @Max(value = 1, message = "status 只能是 0 或 1") Integer status,
        @Size(max = 255, message = "备注最长 255 字符") String remark) {
}
