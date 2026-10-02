package com.lingjiuw.cms.module.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协议适配器公共部分：请求地址拼装、配置校验、响应取字段。
 */
public abstract class AbstractAiProtocolClient implements AiProtocolClient {

    protected final AiHttpClient http;

    protected AbstractAiProtocolClient(AiHttpClient http) {
        this.http = http;
    }

    /** 校验服务商可用并拼出完整请求地址（协议路径由各适配器自己给） */
    protected String endpoint(AiProvider provider, String path) {
        return endpointOf(provider, path);
    }

    /**
     * 与 {@link #endpoint} 同一套校验的静态入口。
     *
     * <p>给不用 {@link AiHttpClient}（整体缓冲，不支持流式）的适配器用：全站 agent 的
     * 流式工具客户端走 JDK {@code HttpClient}，但"服务商是否可用 / base_url 是否合法"
     * 必须是同一把尺子，不能各写一套。
     */
    public static String endpointOf(AiProvider provider, String path) {
        if (provider.getStatus() == null || provider.getStatus() != 1) {
            throw new BizException("服务商「" + provider.getName() + "」已停用");
        }
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            throw new BizException("服务商「" + provider.getName() + "」未配置 base_url");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new BizException("请先在「AI服务商」中填写 " + provider.getName() + " 的 API Key");
        }
        String baseUrl = provider.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        // 全斜杠（"/"、"///"）会被裁成空串，"https://" 会被裁成 "https:" —— 这两种都拼不出可用地址
        if (!StringUtils.hasText(baseUrl) || baseUrl.endsWith(":")) {
            throw new BizException("服务商「" + provider.getName() + "」配置的 base_url 格式非法");
        }
        return baseUrl + path;
    }

    protected Map<String, Object> newBody() {
        return new LinkedHashMap<>();
    }

    protected static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String textValue && !StringUtils.hasText(textValue)) {
            return;
        }
        body.put(key, value);
    }

    /** 取文本字段，缺失或 null 返回 null */
    protected static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /** 取 usage 里的整数字段，缺失返回 null */
    protected static Integer usageInt(JsonNode usage, String field) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        JsonNode node = usage.path(field);
        return node.isNumber() ? node.asInt() : null;
    }
}
