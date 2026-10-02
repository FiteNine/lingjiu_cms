package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具的执行体。参数已经过 {@link ToolArgs#bind} 的 {@code @Valid} 校验（与 HTTP 请求同一套规则）。
 *
 * <p>执行时 {@code SiteContext} 与 {@code SecurityContext} 已由 {@link ToolExecutor} 显式恢复，
 * 所以处理器里可以直接调现有 Service，不必自己 set/clear 上下文。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult execute(JsonNode args) throws Exception;
}
