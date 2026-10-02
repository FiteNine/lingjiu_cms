package com.lingjiuw.cms.module.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 兼容协议。
 * 请求：POST {base_url}/v1/messages，鉴权 x-api-key（官方文档：x-api-key Fully Supported，
 * anthropic-version 被忽略故不发送）；system 提示词走独立的 system 字段，不进 messages。
 * 响应：content[] 块，text / thinking 两类；DeepSeek 文档未列响应结构，按 Anthropic Messages 规范解析。
 * 出处：https://api-docs.deepseek.com/guides/anthropic_api
 */
@Component
public class AnthropicProtocolClient extends AbstractAiProtocolClient {

    /** 官方 base_url 为 https://api.deepseek.com/anthropic，其下再走 Anthropic 的 /v1 路径 */
    private static final String PATH = "/v1/messages";
    /** Anthropic Messages 格式 max_tokens 必填；留空时按官方默认补齐：非思考 8K / 思考 64K */
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final int DEFAULT_THINKING_MAX_TOKENS = 65536;

    public AnthropicProtocolClient(AiHttpClient http) {
        super(http);
    }

    @Override
    public AiProtocol protocol() {
        return AiProtocol.ANTHROPIC;
    }

    @Override
    public ChatResult chat(AiProvider provider, ChatOptions options, List<AiMessage> messages) {
        Map<String, Object> body = newBody();
        body.put("model", options.model());
        body.put("max_tokens", options.maxTokens() != null ? options.maxTokens()
                : (options.thinking() ? DEFAULT_THINKING_MAX_TOKENS : DEFAULT_MAX_TOKENS));
        if (StringUtils.hasText(options.systemPrompt())) {
            body.put("system", options.systemPrompt());
        }
        body.put("messages", messages.stream().map(AiMessage::toMap).toList());

        // thinking 的开关与强度：官方兼容表里 thinking 与 output_config.effort 均标注支持
        body.put("thinking", Map.of("type", options.thinking() ? "enabled" : "disabled"));
        if (options.thinking() && StringUtils.hasText(options.reasoningEffort())) {
            body.put("output_config", Map.of("effort", options.reasoningEffort()));
        }
        putIfPresent(body, "temperature", options.temperature());
        putIfPresent(body, "top_p", options.topP());
        // Anthropic Messages 格式没有 response_format 等价参数，jsonOutput 在此协议下忽略

        JsonNode root = http.postJson(endpoint(provider, PATH),
                Map.of("x-api-key", provider.getApiKey()), body);

        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            boolean isThinking = "thinking".equals(block.path("type").asText());
            // 思考块的文本在 thinking 字段（另附 signature），正文块的文本在 text 字段
            String value = text(block.get(isThinking ? "thinking" : "text"));
            if (value == null) {
                continue;
            }
            if (isThinking) {
                reasoning.append(value);
            } else {
                content.append(value);
            }
        }
        JsonNode usage = root.path("usage");
        return new ChatResult(emptyToNull(content), emptyToNull(reasoning),
                usageInt(usage, "input_tokens"), usageInt(usage, "output_tokens"));
    }

    private static String emptyToNull(StringBuilder value) {
        return value.isEmpty() ? null : value.toString();
    }
}
