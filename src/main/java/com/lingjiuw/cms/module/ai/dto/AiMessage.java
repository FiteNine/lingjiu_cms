package com.lingjiuw.cms.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条对话消息（role + content）。
 * role 取 user / assistant；system 提示词单独放在智能体配置里，由各协议适配器决定拼装位置。
 * content 允许为空：助手回复可能只有思考内容没有正文，前端会把这种回复以空串放回对话历史。
 */
public record AiMessage(
        @NotBlank(message = "消息角色不能为空")
        @Pattern(regexp = "user|assistant", message = "消息角色只能是 user 或 assistant") String role,
        @Size(max = 20000, message = "单条消息最长 20000 字符") String content) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content == null ? "" : content);
        return map;
    }
}
