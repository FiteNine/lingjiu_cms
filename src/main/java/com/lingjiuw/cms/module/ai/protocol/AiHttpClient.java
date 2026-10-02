package com.lingjiuw.cms.module.ai.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * 调用三方服务商的 HTTP 客户端：统一超时、错误转换。
 * 请求体自行序列化，响应统一按 JsonNode 解析（三种协议字段差异大，映射成 DTO 反而更绕）。
 * 仅用于非流式调用：响应会整体缓冲成字符串，不支持 SSE 流式响应。
 */
@Component
public class AiHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 非流式请求期间服务商会持续发空行保活，读超时只在连接真正静默时触发 */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_ERROR_LENGTH = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiHttpClient(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    public JsonNode postJson(String url, Map<String, String> headers, Map<String, Object> body) {
        if (body == null) {
            throw new BizException("请求体不能为空");
        }
        Map<String, String> safeHeaders = headers == null ? Map.of() : headers;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BizException("请求体序列化失败：" + e.getMessage());
        }
        try {
            String response = restClient.post()
                    .uri(url)
                    .headers(target -> safeHeaders.forEach(target::set))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new BizException("服务商返回空响应");
            }
            return objectMapper.readTree(response);
        } catch (RestClientResponseException e) {
            throw new BizException("服务商返回 HTTP " + e.getStatusCode().value() + "：" + errorMessage(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new BizException("调用服务商失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (JsonProcessingException e) {
            throw new BizException("服务商响应解析失败：" + e.getMessage());
        }
    }

    /** 取服务商错误体里的 error.message，取不到就用截断后的原文 */
    private String errorMessage(String body) {
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(body);
                String message = node.path("error").path("message").asText(null);
                if (message == null) {
                    message = node.path("message").asText(null);
                }
                if (message != null) {
                    return message;
                }
            } catch (JsonProcessingException ignored) {
                // 非 JSON 错误体，退回原文
            }
            return body.length() > MAX_ERROR_LENGTH ? body.substring(0, MAX_ERROR_LENGTH) + "..." : body;
        }
        return "无响应内容";
    }
}
