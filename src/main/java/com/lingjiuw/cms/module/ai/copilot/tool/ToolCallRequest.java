package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次待执行的工具调用：模型给出的 toolCallId + 查表得到的 ToolSpec + 已解析的参数。
 *
 * @param toolCallId 模型签发的 id，必须原样回灌（docs/ai-copilot.md §10.4）
 * @param args       模型给的参数；**不保证是合法 JSON 对象**，由具体工具自己 bind 校验
 */
public record ToolCallRequest(String toolCallId, ToolSpec spec, JsonNode args) {

    public String name() {
        return spec.name();
    }
}
