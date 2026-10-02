package com.lingjiuw.cms.module.ai.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起一轮对话。{@code sessionId} 为空表示新建会话（SSE 首帧的 session 事件会回传 id）。
 * 站点不从请求体取：它由 {@code X-Site-Id} 收敛后钉进会话（docs/ai-copilot.md §5.1）。
 */
public record CopilotChatRequest(
        Long sessionId,
        @NotNull(message = "请选择智能体") Long agentId,
        @NotBlank(message = "消息不能为空") @Size(max = 8000, message = "单条消息最长 8000 字符") String message) {
}
