package com.lingjiuw.cms.module.ai.dto;

import com.lingjiuw.cms.module.ai.entity.AiAgent;
import com.lingjiuw.cms.module.ai.entity.AiProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 智能体列表/详情，附带服务商名称与协议便于列表展示 */
public record AgentVO(
        Long id,
        String name,
        String code,
        Long providerId,
        String providerName,
        String protocol,
        String model,
        String systemPrompt,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        Integer thinking,
        String reasoningEffort,
        Integer jsonOutput,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static AgentVO of(AiAgent agent, AiProvider provider) {
        return new AgentVO(agent.getId(), agent.getName(), agent.getCode(), agent.getProviderId(),
                provider == null ? null : provider.getName(),
                provider == null ? null : provider.getProtocol(),
                agent.getModel(), agent.getSystemPrompt(), agent.getTemperature(), agent.getTopP(),
                agent.getMaxTokens(), agent.getThinking(), agent.getReasoningEffort(), agent.getJsonOutput(),
                agent.getStatus(), agent.getRemark(), agent.getCreateTime(), agent.getUpdateTime());
    }
}
