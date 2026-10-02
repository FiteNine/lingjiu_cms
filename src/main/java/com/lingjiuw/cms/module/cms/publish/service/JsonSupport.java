package com.lingjiuw.cms.module.cms.publish.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点发布选项里"值是 JSON 文本"的那几个（{@code pages.static} / {@code facets.combos} /
 * {@code i18n.alternates} / {@code feed.types} / {@code seo.noindexTypes}）。
 *
 * <p>为什么解析要兜底而不是报错：这些值的正主是**后台站点配置**，而站点选项的取值形态由迁移与
 * 后台共同决定，可能是真 JSON、也可能是逗号串（{@code facets.combos}）。发布引擎不是校验收口，
 * 遇到读不出来的值就退回默认值并记一条日志——真正的校验应当发生在保存配置的那一侧。
 * 这条兜底只影响"选项读不出来"，不会静默吞掉模板错误（那类错误照旧从 {@code PublishException} 抛出去）。
 */
@Slf4j
final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    /** 解析一个 JSON 对象；失败返回空表。 */
    static Map<String, Object> parseObject(String text) {
        try {
            return MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("站点选项的 JSON 值读不出来，按空处理：{}", text, e);
            return Map.of();
        }
    }

    /** 解析一个 JSON 数组，元素是按 key 取值用得上泛型的对象；失败返回空列表。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseObjectList(String text) {
        try {
            List<Object> raw = MAPPER.readValue(text, new TypeReference<List<Object>>() {
            });
            List<Map<String, Object>> result = new java.util.ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                Object element = raw.get(i);
                if (element instanceof Map<?, ?> map) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    ((Map<String, Object>) map).forEach((key, value) -> entry.put(String.valueOf(key), value));
                    result.add(entry);
                } else {
                    // 静默跳过 = "配置写错了，对应的静态页/入口少了一个"且没有任何线索
                    log.warn("站点选项的 JSON 数组第 {} 项不是对象，已跳过：{}", i, element);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("站点选项的 JSON 数组读不出来，按空处理：{}", text, e);
            return List.of();
        }
    }
}
