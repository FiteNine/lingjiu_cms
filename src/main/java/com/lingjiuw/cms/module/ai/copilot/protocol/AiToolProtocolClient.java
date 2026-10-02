package com.lingjiuw.cms.module.ai.copilot.protocol;

import com.lingjiuw.cms.module.ai.copilot.model.CopilotMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 带工具调用的流式对话协议适配器（docs/ai-copilot.md §4.5.1）。
 *
 * <p>**一期只实现 OPENAI**（{@code OpenAiToolProtocolClient}）。Anthropic / Responses 只留接缝：
 * 将来接入只需新增一个 {@code @Component} 实现，注册表、循环、权限、审计、前端全都不用改。
 * {@link org.springframework.stereotype.Component} 的实现由
 * {@code AgentLoopService} 按 {@link #protocol()} 装配成 Map。
 */
public interface AiToolProtocolClient {

    AiProtocol protocol();

    /**
     * 发一轮带工具的流式对话，阻塞到流结束。
     *
     * @param tools     本轮暴露给模型的工具（已按角色权限与 tool_scope 过滤）
     * @param listener  增量回调
     * @param cancelled 协作式取消：为 true 时立即停止读取并关闭上游流
     */
    void stream(AiProvider provider, ChatOptions options, List<CopilotMessage> messages,
                List<ToolSchemaView> tools, ToolStreamListener listener, BooleanSupplier cancelled);
}
