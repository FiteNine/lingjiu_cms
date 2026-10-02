package com.lingjiuw.cms.module.ai.copilot.tool;

/**
 * 工具的风险级别。与 MCP 的 tool annotations 对齐：
 * {@code READ → readOnlyHint:true}，{@code WRITE/DESTRUCTIVE → destructiveHint:true}。
 * 前端确认弹窗、审计写入、MCP 对外暴露三处都读同一个字段（docs/ai-copilot.md §4.1）。
 */
public enum ToolRisk {
    /** 只读：不写盘、不写库 */
    READ,
    /** 写入：新增 / 修改业务数据 */
    WRITE,
    /** 破坏性：删除、全站发布、覆盖站点文件 */
    DESTRUCTIVE;

    /** 需要人工确认才允许执行。 */
    public boolean requiresConfirm() {
        return this == WRITE || this == DESTRUCTIVE;
    }
}
