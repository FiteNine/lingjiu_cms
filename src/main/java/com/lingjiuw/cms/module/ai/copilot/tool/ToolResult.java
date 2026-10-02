package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具的一次调用能返回的东西。
 *
 * @param ok     是否成功；失败时 {@code error} 会作为工具结果回灌给模型（isError:true 的语义）
 * @param summary 一句话摘要，给前端的工具卡片显示
 * @param data    结构化结果，会序列化后回灌给模型；可为 null
 * @param error   失败原因；成功时可为 null
 */
public record ToolResult(boolean ok, String summary, JsonNode data, String error) {

    public static ToolResult ok(String summary, JsonNode data) {
        return new ToolResult(true, summary, data, null);
    }

    public static ToolResult fail(String error) {
        return new ToolResult(false, null, null, error);
    }
}
