package com.lingjiuw.cms.module.ai.copilot.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 工具注册表：Spring 注入的全部 {@link ToolProvider} 汇总成一张按名字索引的表。
 *
 * <p>一次对话真正暴露给模型的工具 = 注册表 ∩ {@code ai_agent.tool_scope} 给出的组
 * ∩ 当前用户有权限的那些（后者在 {@code CopilotService} 组装提示词时按 perms 过滤）。
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolSpec> byName;

    public ToolRegistry(List<ToolProvider> providers) {
        Map<String, ToolSpec> map = new LinkedHashMap<>();
        for (ToolProvider provider : providers) {
            for (ToolSpec spec : provider.tools()) {
                ToolSpec duplicate = map.putIfAbsent(spec.name(), spec);
                if (duplicate != null) {
                    throw new IllegalStateException("工具名重复：" + spec.name());
                }
            }
        }
        this.byName = Map.copyOf(map);
    }

    public List<ToolSpec> all() {
        return List.copyOf(byName.values());
    }

    public Optional<ToolSpec> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * 某个 {@code tool_scope} 下暴露的工具：空 = 默认组，非空 = 恰好列出的那些组。
     * 组名写错直接抛异常（配置错误要立刻可见，不能静默少给工具）。
     */
    public List<ToolSpec> forScope(String toolScope) {
        Set<ToolGroup> groups = ToolGroup.parseScope(toolScope);
        return byName.values().stream().filter(spec -> groups.contains(spec.group())).toList();
    }
}
