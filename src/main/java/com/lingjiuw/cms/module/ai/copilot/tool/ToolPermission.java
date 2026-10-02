package com.lingjiuw.cms.module.ai.copilot.tool;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工具级权限判定：纯 perms 集合判断，O(1)，零查库。
 *
 * <p><b>必须与后端语义对齐，不能抄前端的 admin 旁路。</b>后端 {@code selectPermsByUserId}
 * 没有 admin 例外，admin 全通过是因为种子数据把全部菜单授给了 role 1。工具层照此办理：
 * 只看 perms 集合，绝不额外放行 admin——否则会出现"后端 403 但 AI 以为能做"。
 */
@Component
public class ToolPermission {

    public boolean allowed(ToolSpec spec, Set<String> perms) {
        String required = spec.permission();
        if (required == null || required.isBlank()) {
            return true;
        }
        return perms != null && perms.contains(required);
    }
}
