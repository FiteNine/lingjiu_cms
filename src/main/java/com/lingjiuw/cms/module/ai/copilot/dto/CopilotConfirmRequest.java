package com.lingjiuw.cms.module.ai.copilot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 危险操作的人工确认（docs/ai-copilot.md §5.2）。
 *
 * <p>超时（5 分钟）按 DENY 处理，回灌一条"用户未在时限内确认，操作已取消"给模型，
 * 让它换路子或收尾——被拒绝时回灌的是**工具结果**（isError），不是协议错误。
 *
 * @param argsOverride 用户在弹窗里改了参数再批准时带回来；为空表示按模型原参数执行
 */
public record CopilotConfirmRequest(
        @NotNull(message = "会话 id 不能为空") Long sessionId,
        @NotBlank(message = "toolCallId 不能为空") String toolCallId,
        @NotBlank(message = "decision 不能为空")
        @Pattern(regexp = "ALLOW|DENY", message = "decision 只能是 ALLOW 或 DENY") String decision,
        JsonNode argsOverride) {

    public boolean allow() {
        return "ALLOW".equals(decision);
    }
}
