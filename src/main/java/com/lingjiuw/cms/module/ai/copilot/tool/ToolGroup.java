package com.lingjiuw.cms.module.ai.copilot.tool;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 工具分组，用于 {@code ai_agent.tool_scope} 的组白名单（docs/ai-copilot.md §3.4、§4.2）。
 *
 * <p>CONTENT / TEMPLATE_READ / SYSTEM_READ 是默认组；PLATFORM 是"改架构"类，默认不暴露，
 * 必须在 {@code tool_scope} 里显式打开——否则"改内容"的授权会被顺带升级成"改整站形态"。
 */
public enum ToolGroup {
    CONTENT,
    TEMPLATE_READ,
    SYSTEM_READ,
    PLATFORM;

    /** 未配置 {@code tool_scope} 时默认暴露的组。 */
    public static final Set<ToolGroup> DEFAULT = Set.of(CONTENT, TEMPLATE_READ, SYSTEM_READ);

    /** 解析逗号分隔的组名；无法识别即抛异常（配置写错要立刻可见，不能静默少给工具）。 */
    public static Set<ToolGroup> parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT;
        }
        Set<ToolGroup> groups = new LinkedHashSet<>();
        for (String raw : scope.split(",")) {
            String name = raw.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            try {
                groups.add(valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("未知的工具组：" + raw.trim());
            }
        }
        return groups.isEmpty() ? DEFAULT : Set.copyOf(groups);
    }
}
