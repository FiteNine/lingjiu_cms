package com.lingjiuw.cms.module.ai.protocol;

import com.lingjiuw.cms.common.exception.BizException;

/**
 * 支持的对话协议。新增第三方服务商时，只要它兼容三种协议之一，加数据行即可；
 * 出现全新协议时，实现一个 {@link AiProtocolClient} 并在此登记。
 */
public enum AiProtocol {

    /** OpenAI Chat Completions 兼容：POST {base_url}/chat/completions */
    OPENAI,

    /** Anthropic Messages 兼容：POST {base_url}/v1/messages */
    ANTHROPIC,

    /** OpenAI Responses 兼容：POST {base_url}/responses */
    RESPONSES;

    public static AiProtocol of(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null) {
            for (AiProtocol protocol : values()) {
                if (protocol.name().equalsIgnoreCase(normalized)) {
                    return protocol;
                }
            }
        }
        throw new BizException("不支持的协议：" + value);
    }
}
