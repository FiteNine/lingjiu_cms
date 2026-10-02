package com.lingjiuw.cms.module.ai.copilot.model;

import java.util.List;

/**
 * Agent 循环内部使用的一条对话消息（与三种供应商协议的线格式解耦）。
 *
 * <p>{@code role} ∈ system / user / assistant / tool。
 * {@code reasoningContent} 与 {@code toolCalls} / {@code toolCallId} 是历史回灌能否被服务端
 * 接受的关键，必须原样存取（docs/ai-copilot.md §4.5.3、§10.4）。
 */
public record CopilotMessage(
        String role,
        String content,
        String reasoningContent,
        List<ToolCall> toolCalls,
        String toolCallId) {

    public static CopilotMessage system(String content) {
        return new CopilotMessage("system", content, null, null, null);
    }

    public static CopilotMessage user(String content) {
        return new CopilotMessage("user", content, null, null, null);
    }

    public static CopilotMessage assistant(String content, String reasoningContent, List<ToolCall> toolCalls) {
        return new CopilotMessage("assistant", content, reasoningContent, toolCalls, null);
    }

    /** 工具结果回灌：{@code toolCallId} 必须是模型签发的那一个，不截断、不重命名、不重新生成。 */
    public static CopilotMessage tool(String toolCallId, String content) {
        return new CopilotMessage("tool", content, null, null, toolCallId);
    }
}
