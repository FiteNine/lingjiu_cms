package com.lingjiuw.cms.module.ai.dto;

import com.lingjiuw.cms.module.ai.entity.AiProvider;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** AI 服务商列表/详情；API Key 不回明文，只回掩码 */
public record ProviderVO(
        Long id,
        String name,
        String code,
        String protocol,
        String baseUrl,
        String apiKeyMasked,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static ProviderVO of(AiProvider provider) {
        return new ProviderVO(provider.getId(), provider.getName(), provider.getCode(), provider.getProtocol(),
                provider.getBaseUrl(), mask(provider.getApiKey()), provider.getStatus(), provider.getRemark(),
                provider.getCreateTime(), provider.getUpdateTime());
    }

    private static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        if (apiKey.length() <= 12) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
