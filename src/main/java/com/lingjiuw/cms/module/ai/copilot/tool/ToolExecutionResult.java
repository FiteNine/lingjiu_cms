package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次工具执行的结果（内部载体）。
 *
 * @param content 回灌给模型的文本（成功是 data 的 JSON，失败是错误说明）
 * @param summary 给前端工具卡片看的摘要
 * @param status  1 成功 / 0 失败 / 2 被用户拒绝 / 3 无权限
 * @param siteId  实际生效的站点 id —— 每个工具结果都回显它，
 *                避免"AI 说写成功了、实际写进了默认站点"（docs/ai-copilot.md 铁律 3）
 */
public record ToolExecutionResult(
        String toolCallId,
        String name,
        String title,
        boolean ok,
        String summary,
        String content,
        String error,
        long durationMs,
        long siteId,
        int status,
        ToolRisk risk,
        JsonNode data) {

    public static final int STATUS_OK = 1;
    public static final int STATUS_FAIL = 0;
    public static final int STATUS_DENIED = 2;
    public static final int STATUS_FORBIDDEN = 3;
}
