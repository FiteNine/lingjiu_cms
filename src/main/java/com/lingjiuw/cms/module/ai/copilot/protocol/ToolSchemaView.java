package com.lingjiuw.cms.module.ai.copilot.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 暴露给模型的工具描述（协议侧视角）。JSON Schema 由 {@code ToolArgs} 从 DTO 派生，
 * 这里只是把 name / description / parameters 三件套递给协议适配器。
 */
public record ToolSchemaView(String name, String description, JsonNode parameters) {
}
