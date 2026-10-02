package com.lingjiuw.cms.module.ai.copilot.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.copilot.model.CopilotMessage;
import com.lingjiuw.cms.module.ai.copilot.model.ToolCall;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.protocol.AbstractAiProtocolClient;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI Chat Completions 兼容协议的**带工具流式**适配器（docs/ai-copilot.md §7.1）。
 *
 * <p>与 {@code OpenAiProtocolClient}（整体缓冲、不支持 tools）刻意分成两个类：
 * 后者的试聊仍在用，保持原样；这里用 JDK {@code HttpClient} + {@code BodyHandlers.ofLines()}
 * 拿到真正的首字节流式（配合 {@code stream=true}），首字节不再等于全量生成时间。
 *
 * <p>一期只处理 OPENAI 一种 delta 形状；Anthropic / Responses 的 delta 形状记在
 * docs/ai-copilot.md §7.1 的表里，将来新增一个 {@code @Component} 实现即可。
 */
@Slf4j
@Component
public class OpenAiToolProtocolClient implements AiToolProtocolClient {

    /** 官方 base_url 不带 /v1，请求路径即 /chat/completions */
    private static final String PATH = "/chat/completions";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 与 AiHttpClient 的读超时口径一致：非流式时服务商也会发空行保活，只在真正静默时触发 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_ERROR_LENGTH = 500;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public OpenAiToolProtocolClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProtocol protocol() {
        return AiProtocol.OPENAI;
    }

    @Override
    public void stream(AiProvider provider, ChatOptions options, List<CopilotMessage> messages,
                       List<ToolSchemaView> tools, ToolStreamListener listener, BooleanSupplier cancelled) {
        String url = AbstractAiProtocolClient.endpointOf(provider, PATH);
        try {
            doStream(provider, url, payload(options, messages, tools, false), listener, cancelled);
        } catch (BizException e) {
            // docs/ai-copilot.md §4.5.3 的自动降级：默认不回传 reasoning_content（省 token、不污染上下文）。
            // 服务端若因此报 400，补上历史里的 reasoning_content 重试一次。
            // 这行 WARN 同时是"某个 tool_call_id 服务端不认识"的信号（§10.4）。
            if (e.getMessage() != null && e.getMessage().contains("reasoning_content")) {
                log.warn("历史回灌被服务商拒绝（提示 reasoning_content），补上思考内容重试一次：{}", e.getMessage());
                doStream(provider, url, payload(options, messages, tools, true), listener, cancelled);
                return;
            }
            throw e;
        }
    }

    private void doStream(AiProvider provider, String url, String body,
                          ToolStreamListener listener, BooleanSupplier cancelled) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + provider.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        DeltaAccumulator accumulator = new DeltaAccumulator();
        try {
            HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String error = response.body().limit(20).collect(Collectors.joining("\n"));
                throw new BizException("服务商返回 HTTP " + response.statusCode() + "：" + errorMessage(error));
            }
            try (Stream<String> lines = response.body()) {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    if (cancelled.getAsBoolean()) {
                        break;
                    }
                    String data = dataOf(iterator.next());
                    if (data == null) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode chunk;
                    try {
                        chunk = objectMapper.readTree(data);
                    } catch (JsonProcessingException e) {
                        // 网关偶尔插一行非 JSON 的心跳：跳过它，不让整条流以 500 结束
                        log.debug("忽略无法解析的流分片：{}", abbreviate(data));
                        continue;
                    }
                    accumulator.accept(chunk, listener);
                }
            }
            listener.onFinish(accumulator.result());
        } catch (IOException e) {
            throw new BizException("调用服务商失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("调用服务商被中断");
        }
    }

    /** 从一行 SSE 里取出 data 段；注释帧（心跳）、空行、其它字段一律返回 null。 */
    private static String dataOf(String line) {
        if (line == null || line.isBlank() || line.charAt(0) == ':') {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        String data = line.substring("data:".length()).trim();
        return data.isEmpty() ? null : data;
    }

    /**
     * 组装请求体。{@code includeReasoning} 为 true 时把 assistant 历史里的
     * {@code reasoning_content} 一并回传（只在 400 自动降级重试时用一次）。
     */
    private String payload(ChatOptions options, List<CopilotMessage> messages,
                           List<ToolSchemaView> tools, boolean includeReasoning) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());

        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        if (StringUtils.hasText(options.systemPrompt())) {
            payloadMessages.add(Map.of("role", "system", "content", options.systemPrompt()));
        }
        for (CopilotMessage message : messages) {
            payloadMessages.add(wireMessage(message, includeReasoning));
        }
        body.put("messages", payloadMessages);

        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        // 思考模式默认开启（官方默认 enabled），只有显式关闭才发 disabled
        body.put("thinking", Map.of("type", options.thinking() ? "enabled" : "disabled"));
        if (options.thinking()) {
            putIfNotBlank(body, "reasoning_effort", options.reasoningEffort());
        }
        putIfNotBlank(body, "temperature", options.temperature());
        putIfNotBlank(body, "top_p", options.topP());
        putIfNotBlank(body, "max_tokens", options.maxTokens());

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> defs = new ArrayList<>(tools.size());
            for (ToolSchemaView tool : tools) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.put("parameters", tool.parameters());
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("type", "function");
                def.put("function", function);
                defs.add(def);
            }
            body.put("tools", defs);
            body.put("tool_choice", "auto");
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BizException("请求体序列化失败：" + e.getMessage());
        }
    }

    private static Map<String, Object> wireMessage(CopilotMessage message, boolean includeReasoning) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", message.role());
        if ("tool".equals(message.role())) {
            // tool_call_id 原样回灌，不截断、不重命名、不重新生成（§4.5.3）
            row.put("tool_call_id", message.toolCallId());
            row.put("content", message.content() == null ? "" : message.content());
            return row;
        }
        row.put("content", message.content() == null ? "" : message.content());
        if ("assistant".equals(message.role())) {
            if (includeReasoning && message.reasoningContent() != null) {
                row.put("reasoning_content", message.reasoningContent());
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>(message.toolCalls().size());
                for (ToolCall call : message.toolCalls()) {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", call.name());
                    // arguments 必须是 JSON 文本（字符串），不是对象
                    function.put("arguments", call.arguments() == null ? "{}" : call.arguments());
                    Map<String, Object> rowCall = new LinkedHashMap<>();
                    rowCall.put("id", call.id());
                    rowCall.put("type", "function");
                    rowCall.put("function", function);
                    calls.add(rowCall);
                }
                row.put("tool_calls", calls);
            }
        }
        return row;
    }

    private static void putIfNotBlank(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        body.put(key, value);
    }

    private static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "无响应内容";
        }
        return body.length() > MAX_ERROR_LENGTH ? body.substring(0, MAX_ERROR_LENGTH) + "..." : body;
    }

    private static String abbreviate(String value) {
        return value.length() > 120 ? value.substring(0, 120) + "..." : value;
    }

    /**
     * 把一个 SSE 分片并进累加器（docs/ai-copilot.md §7.1.1）。
     *
     * <p><b>已实测确认</b>：一次 48 字符的 {@code arguments} 跨了 27 个分片到达。
     * {@code function.arguments} 是流式截断的 JSON 片段，**不能边收边 parse**；
     * 按 {@code index} 分片拼接，等 {@code finish_reason} 之后再整体解析。
     *
     * <p>包级可见是为了单测能直接喂分片（见 {@code OpenAiToolProtocolClientTest}）。
     */
    static final class DeltaAccumulator {

        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();
        private String finishReason;
        private Integer inputTokens;
        private Integer outputTokens;

        void accept(JsonNode root, ToolStreamListener listener) {
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                inputTokens = number(usage.get("prompt_tokens"), inputTokens);
                outputTokens = number(usage.get("completion_tokens"), outputTokens);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                // include_usage 的最后一帧没有 choices，只有 usage —— 它不是"无输出"
                return;
            }
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            String reasoningDelta = text(delta.get("reasoning_content"));
            if (reasoningDelta != null) {
                reasoning.append(reasoningDelta);
                listener.onReasoning(reasoningDelta);
            }
            String contentDelta = text(delta.get("content"));
            if (contentDelta != null) {
                content.append(contentDelta);
                listener.onDelta(contentDelta);
            }
            JsonNode calls = delta.path("tool_calls");
            if (calls.isArray()) {
                for (JsonNode call : calls) {
                    acceptToolCall(call);
                }
            }
            String reason = text(choice.get("finish_reason"));
            if (reason != null) {
                finishReason = reason;
            }
        }

        private void acceptToolCall(JsonNode call) {
            int index = call.path("index").asInt(0);
            ToolCallBuilder builder = toolCalls.computeIfAbsent(index, key -> new ToolCallBuilder());
            String id = text(call.get("id"));
            if (id != null) {
                builder.id = id;
            }
            JsonNode function = call.path("function");
            String name = text(function.get("name"));
            if (name != null) {
                // 名字通常只在首片给全；个别网关会分片给，这时按"更长的那个"收敛
                if (builder.name == null || name.startsWith(builder.name)) {
                    builder.name = name;
                }
            }
            String arguments = text(function.get("arguments"));
            if (arguments != null) {
                builder.arguments.append(arguments);
            }
        }

        ToolStreamResult result() {
            List<ToolCall> calls = new ArrayList<>(toolCalls.size());
            for (ToolCallBuilder builder : toolCalls.values()) {
                calls.add(new ToolCall(builder.id, builder.name, builder.arguments.toString()));
            }
            return new ToolStreamResult(finishReason, calls, inputTokens, outputTokens);
        }

        private static String text(JsonNode node) {
            return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
        }

        private static Integer number(JsonNode node, Integer fallback) {
            return node != null && node.isNumber() ? node.asInt() : fallback;
        }

        private static final class ToolCallBuilder {
            private String id;
            private String name;
            private final StringBuilder arguments = new StringBuilder();
        }
    }
}
