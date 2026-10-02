package com.lingjiuw.cms.module.ai.copilot.model;

/**
 * 模型签发的一次工具调用。
 *
 * @param id        服务端签发的 tool_call_id（形如 {@code call_00_xxxxxxxx}），**原样存取**
 * @param name      工具名
 * @param arguments JSON 文本（不是对象）——流式下是跨片拼接出来的，可能不合法
 */
public record ToolCall(String id, String name, String arguments) {
}
