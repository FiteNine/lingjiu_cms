package com.lingjiuw.cms.module.ai.dto;

import com.lingjiuw.cms.module.ai.entity.AiAgent;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 一次对话请求的模型参数（与实体解耦，连通性测试也用同一套参数）。
 */
public record ChatOptions(
        String model,
        String systemPrompt,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        boolean thinking,
        String reasoningEffort,
        boolean jsonOutput) {

    public static ChatOptions of(AiAgent agent) {
        Objects.requireNonNull(agent, "agent 不能为空");
        return new ChatOptions(agent.getModel(), agent.getSystemPrompt(), agent.getTemperature(),
                agent.getTopP(), agent.getMaxTokens(), isOn(agent.getThinking()),
                agent.getReasoningEffort(), isOn(agent.getJsonOutput()));
    }

    /** 服务商连通性测试：关闭思考、只要最少输出 */
    public static ChatOptions probe(String model) {
        return new ChatOptions(model, null, null, null, 16, false, null, false);
    }

    private static boolean isOn(Integer flag) {
        return flag != null && flag == 1;
    }
}
