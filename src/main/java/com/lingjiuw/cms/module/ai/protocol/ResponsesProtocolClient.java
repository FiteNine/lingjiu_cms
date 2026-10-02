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
 * OpenAI Responses 兼容协议。
 * 请求：POST {base_url}/responses，鉴权 Authorization: Bearer；system 提示词走 instructions 字段，
 * 对话历史放 input 数组（官方声明无状态，多轮需客户端带全量历史）。
 * 思考强度用 reasoning.effort，none 即关闭思考（官方默认 high）。
 * 响应：output[] 里 message 项取 output_text，reasoning 项取思考文本。
 * 出处：https://api-docs.deepseek.com/api/create-response 、https://api-docs.deepseek.com/guides/responses_api
 */
@Component
public class ResponsesProtocolClient extends AbstractAiProtocolClient {

    private static final String PATH = "/responses";
    /** 官方默认思考强度 */
    private static final String DEFAULT_EFFORT = "high";

    public ResponsesProtocolClient(AiHttpClient http) {
        super(http);
    }

    @Override
    public AiProtocol protocol() {
        return AiProtocol.RESPONSES;
    }

    @Override
    public ChatResult chat(AiProvider provider, ChatOptions options, List<AiMessage> messages) {
        Map<String, Object> body = newBody();
        body.put("model", options.model());
        body.put("input", messages.stream().map(AiMessage::toMap).toList());
        if (StringUtils.hasText(options.systemPrompt())) {
            body.put("instructions", options.systemPrompt());
        }
        String effort = options.thinking()
                ? (StringUtils.hasText(options.reasoningEffort()) ? options.reasoningEffort() : DEFAULT_EFFORT)
                : "none";
        body.put("reasoning", Map.of("effort", effort));
        putIfPresent(body, "max_output_tokens", options.maxTokens());
        putIfPresent(body, "temperature", options.temperature());
        putIfPresent(body, "top_p", options.topP());
        if (options.jsonOutput()) {
            body.put("text", Map.of("format", Map.of("type", "json_object")));
        }

        JsonNode root = http.postJson(endpoint(provider, PATH),
                Map.of("Authorization", "Bearer " + provider.getApiKey()), body);

        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            boolean isReasoning = "reasoning".equals(item.path("type").asText());
            StringBuilder target = isReasoning ? reasoning : content;
            int before = target.length();
            appendText(target, item.path("content"));
            // 思考文本：官方 Responses 规范放在 summary[]，DeepSeek 实现放在 content[]；先取 content，为空再取 summary，避免重复
            if (isReasoning && target.length() == before) {
                appendText(target, item.path("summary"));
            }
        }
        JsonNode usage = root.path("usage");
        return new ChatResult(content.isEmpty() ? null : content.toString(),
                reasoning.isEmpty() ? null : reasoning.toString(),
                usageInt(usage, "input_tokens"), usageInt(usage, "output_tokens"));
    }

    private static void appendText(StringBuilder target, JsonNode parts) {
        for (JsonNode part : parts) {
            String value = text(part.get("text"));
            if (value != null) {
                target.append(value);
            }
        }
    }
}
