package com.lingjiuw.cms.module.ai.copilot.dto;

import com.lingjiuw.cms.module.ai.entity.AiAgent;

/**
 * 全站 agent 的智能体下拉选项（docs/ai-copilot.md §5.3）。
 *
 * <p>**刻意不回 {@code systemPrompt}**：登录即可读这个接口，系统提示词属于配置细节。
 */
public record AgentOptionVO(
        Long id,
        String name,
        String code,
        String model,
        String protocol,
        Integer thinking,
        Integer jsonOutput,
        Integer toolEnabled,
        String toolScope) {

    public static AgentOptionVO of(AiAgent agent, String protocol) {
        return new AgentOptionVO(agent.getId(), agent.getName(), agent.getCode(), agent.getModel(),
                protocol, agent.getThinking(), agent.getJsonOutput(), agent.getToolEnabled(),
                agent.getToolScope());
    }
}
