package com.lingjiuw.cms.module.ai.copilot.dto;

import com.lingjiuw.cms.module.ai.copilot.entity.AiChatMessage;

import java.time.LocalDateTime;

/**
 * 会话回放的一条消息。{@code reasoningContent} / {@code toolCalls} / {@code toolCallId}
 * 原样返回，不做任何截断或加工（docs/ai-copilot.md §10.4）。
 */
public record CopilotMessageVO(
        Long id,
        Integer seq,
        String role,
        String content,
        String reasoningContent,
        String toolCalls,
        String toolCallId,
        String toolName,
        String toolArgs,
        Integer toolStatus,
        Integer toolDurationMs,
        LocalDateTime createTime) {

    public static CopilotMessageVO of(AiChatMessage message) {
        return new CopilotMessageVO(message.getId(), message.getSeq(), message.getRole(),
                message.getContent(), message.getReasoningContent(), message.getToolCalls(),
                message.getToolCallId(), message.getToolName(), message.getToolArgs(),
                message.getToolStatus(), message.getToolDurationMs(), message.getCreateTime());
    }
}
