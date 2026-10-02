package com.lingjiuw.cms.module.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 兼容协议。
 * 请求：POST {base_url}/chat/completions，鉴权 Authorization: Bearer；system 提示词作为 messages 首条。
 * 响应：choices[0].message.content 与同级的 reasoning_content。
 * 出处：https://api-docs.deepseek.com/api/create-chat-completion
 */
@Component
public class OpenAiProtocolClient extends AbstractAiProtocolClient {

    /** 官方 base_url 不带 /v1，请求路径即 /chat/completions */
    private static final String PATH = "/chat/completions";

    public OpenAiProtocolClient(AiHttpClient http) {
        super(http);
    }

    @Override
    public AiProtocol protocol() {
        return AiProtocol.OPENAI;
    }

    @Override
    public ChatResult chat(AiProvider provider, ChatOptions options, List<AiMessage> messages) {
        Map<String, Object> body = newBody();
        body.put("model", options.model());

        List<Map<String, Object>> payload = new ArrayList<>();
        if (StringUtils.hasText(options.systemPrompt())) {
            payload.add(Map.of("role", "system", "content", options.systemPrompt()));
        }
        messages.forEach(message -> payload.add(message.toMap()));
        body.put("messages", payload);

        // 思考模式默认开启（官方默认 enabled），只有显式关闭才发 disabled
        body.put("thinking", Map.of("type", options.thinking() ? "enabled" : "disabled"));
        if (options.thinking()) {
            putIfPresent(body, "reasoning_effort", options.reasoningEffort());
        }
        putIfPresent(body, "temperature", options.temperature());
        putIfPresent(body, "top_p", options.topP());
        putIfPresent(body, "max_tokens", options.maxTokens());
        if (options.jsonOutput()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        JsonNode root = http.postJson(endpoint(provider, PATH),
                Map.of("Authorization", "Bearer " + provider.getApiKey()), body);

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            // 网关/代理常以 HTTP 200 返回带 error 字段的故障响应，不能当成「模型无输出」
            String error = text(root.path("error").path("message"));
            throw new BizException("服务商未返回对话结果" + (error == null ? "" : "：" + error));
        }
        JsonNode message = choices.path(0).path("message");
        JsonNode usage = root.path("usage");
        return new ChatResult(text(message.get("content")), text(message.get("reasoning_content")),
                usageInt(usage, "prompt_tokens"), usageInt(usage, "completion_tokens"));
    }
}
