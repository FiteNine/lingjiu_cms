package com.lingjiuw.cms.module.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 新增/编辑智能体请求。数值留空表示不传该参数，由服务商按官方默认处理。 */
public record AgentSaveRequest(
        @NotBlank(message = "智能体名称不能为空") @Size(max = 64, message = "智能体名称最长 64 字符") String name,
        @NotBlank(message = "智能体标识不能为空")
        @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "智能体标识只能用小写字母、数字、下划线或短横线")
        String code,
        @NotNull(message = "请选择 AI 服务商") Long providerId,
        @NotBlank(message = "模型不能为空") @Size(max = 64, message = "模型名最长 64 字符") String model,
        @Size(max = 20000, message = "系统提示词最长 20000 字符") String systemPrompt,
        @DecimalMin(value = "0.00", message = "temperature 不能小于 0") @DecimalMax(value = "2.00", message = "temperature 不能大于 2") BigDecimal temperature,
        @DecimalMin(value = "0.00", inclusive = false, message = "top_p 必须大于 0") @DecimalMax(value = "1.00", message = "top_p 不能大于 1") BigDecimal topP,
        @Min(value = 1, message = "max_tokens 不能小于 1") @Max(value = 393216, message = "max_tokens 不能大于 393216") Integer maxTokens,
        @Min(value = 0, message = "thinking 只能是 0 或 1") @Max(value = 1, message = "thinking 只能是 0 或 1") Integer thinking,
        String reasoningEffort,
        @Min(value = 0, message = "jsonOutput 只能是 0 或 1") @Max(value = 1, message = "jsonOutput 只能是 0 或 1") Integer jsonOutput,
        @Min(value = 0, message = "toolEnabled 只能是 0 或 1") @Max(value = 1, message = "toolEnabled 只能是 0 或 1") Integer toolEnabled,
        @Size(max = 255, message = "工具范围最长 255 字符") String toolScope,
        @Min(value = 0, message = "status 只能是 0 或 1") @Max(value = 1, message = "status 只能是 0 或 1") Integer status,
        @Size(max = 255, message = "备注最长 255 字符") String remark) {
}
