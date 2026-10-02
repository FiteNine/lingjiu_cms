package com.lingjiuw.cms.module.ai.copilot.dto;

import com.lingjiuw.cms.module.ai.copilot.entity.AiChatSession;

import java.time.LocalDateTime;

/** 会话列表 / 详情。siteName 与 agentName 由调用方补齐（库里只存 id）。 */
public record CopilotSessionVO(
        Long id,
        String title,
        Long siteId,
        String siteName,
        Long agentId,
        String agentName,
        Integer status,
        Integer rounds,
        Integer toolCallCount,
        Long inputTokens,
        Long outputTokens,
        LocalDateTime createTime) {

    public static CopilotSessionVO of(AiChatSession session, String siteName, String agentName) {
        return new CopilotSessionVO(session.getId(), session.getTitle(), session.getSiteId(), siteName,
                session.getAgentId(), agentName, session.getStatus(), session.getRounds(),
                session.getToolCallCount(), session.getInputTokens(), session.getOutputTokens(),
                session.getCreateTime());
    }
}
