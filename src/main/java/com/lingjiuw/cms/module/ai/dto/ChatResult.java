package com.lingjiuw.cms.module.ai.dto;

/**
 * 三种协议归一化后的对话结果。content 为正文，reasoningContent 为思考内容（可空）。
 */
public record ChatResult(
        String content,
        String reasoningContent,
        Integer inputTokens,
        Integer outputTokens) {
}
