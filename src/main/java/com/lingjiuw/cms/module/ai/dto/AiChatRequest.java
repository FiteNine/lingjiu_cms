package com.lingjiuw.cms.module.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 智能体试聊请求：携带完整对话历史（各协议均无服务端会话存储） */
public record AiChatRequest(
        @NotEmpty(message = "消息不能为空")
        @Size(max = 100, message = "消息条数过多，最多 100 条")
        @Valid List<AiMessage> messages) {
}
