package com.lingjiuw.cms.module.ai.copilot.protocol;

import com.lingjiuw.cms.module.ai.copilot.model.ToolCall;

import java.util.List;

/**
 * 一轮带工具的流式对话结束时的归一化结果。
 *
 * @param finishReason {@code tool_calls} → 进入执行分支；{@code stop} → 收尾；可能为 null
 * @param toolCalls    分片攒完后拼出来的工具调用；为空表示本轮是纯文本回复
 */
public record ToolStreamResult(
        String finishReason,
        List<ToolCall> toolCalls,
        Integer inputTokens,
        Integer outputTokens) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
