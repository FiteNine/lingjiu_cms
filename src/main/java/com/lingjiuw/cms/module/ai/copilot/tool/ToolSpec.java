package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 一个工具的声明（docs/ai-copilot.md §4.1）。按 MCP Tool 语义设计：
 * {@code inputSchema} 是标准 JSON Schema，{@code risk} 映射到 MCP 的 tool annotations，
 * 第二阶段对外暴露标准 MCP Server 时直接拿这张表即可。
 *
 * <p>{@code permission} 直接复用 Controller 上那个 {@code @PreAuthorize} 的权限串；
 * 为 null 表示"仅登录"。判定见 {@link ToolPermission}。
 *
 * <p>{@code permission} 的再判一次是刻意的重复：工具调用不经过 Spring MVC 的方法拦截链，
 * {@code @PreAuthorize} 根本不会被触发。
 */
public final class ToolSpec {

    private final String name;
    private final String title;
    private final String description;
    private final String permission;
    private final ToolRisk risk;
    private final ToolGroup group;
    private final ObjectNode inputSchema;
    private final ToolHandler handler;

    private ToolSpec(Builder builder) {
        this.name = builder.name;
        this.title = builder.title == null ? builder.name : builder.title;
        this.description = builder.description;
        this.permission = builder.permission;
        this.risk = builder.risk;
        this.group = builder.group;
        this.inputSchema = builder.inputSchema;
        this.handler = builder.handler;
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public ToolRisk risk() {
        return risk;
    }

    public ToolGroup group() {
        return group;
    }

    public ObjectNode inputSchema() {
        return inputSchema;
    }

    public ToolHandler handler() {
        return handler;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** 链式构造器；必填 name / description / risk / group / schema / handler。 */
    public static final class Builder {

        private final String name;
        private String title;
        private String description;
        private String permission;
        private ToolRisk risk;
        private ToolGroup group;
        private ObjectNode inputSchema;
        private ToolHandler handler;

        private Builder(String name) {
            this.name = name;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** 与 Controller 上那个 {@code @PreAuthorize} 一模一样；留空 = 仅登录。 */
        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder risk(ToolRisk risk) {
            this.risk = risk;
            return this;
        }

        public Builder group(ToolGroup group) {
            this.group = group;
            return this;
        }

        public Builder schema(ObjectNode inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder handler(ToolHandler handler) {
            this.handler = handler;
            return this;
        }

        public ToolSpec build() {
            if (description == null || description.isBlank()) {
                throw new IllegalStateException("工具 " + name + " 缺少 description");
            }
            if (risk == null || group == null) {
                throw new IllegalStateException("工具 " + name + " 缺少 risk / group");
            }
            if (inputSchema == null || handler == null) {
                throw new IllegalStateException("工具 " + name + " 缺少 schema / handler");
            }
            return new ToolSpec(this);
        }
    }
}
