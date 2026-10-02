package com.lingjiuw.cms.module.cms.publish.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * jsonb 列的读取工具（static-publish.md §2.3 的 {@code data} / {@code content_toc}、§2.1 的
 * {@code options}、§2.7 的选项值）。
 *
 * <p>为什么集中在一处：这些列在实体里都是 {@code String}（PostgreSQL 的 jsonb 不接受
 * varchar 隐式赋值，写入走 XML 的 {@code ::jsonb}），读取时**必须**容忍三种东西——
 * null、不是 JSON 的字符串、以及"JSON 是标量而不是对象/数组"。任何一处漏判都会让
 * 一条脏数据把整批发布打挂，而 §1.3 的要求是"缺失的可选数据永不抛异常"。
 */
@Slf4j
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /** 解析任意 JSON；不是 JSON 时返回 null（调用方按"没有这个值"处理）。 */
    static Object parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception e) {
            // jsonb 列被外部直接 UPDATE 成非法 JSON 时，这里以前是彻底静默的：
            // 现象是"内容/选项莫名丢失"，排查时无从下手。记一条带长度与异常的日志。
            log.warn("jsonb 列的内容不是合法 JSON，按没有这个值处理（{} 字符）：{}", text.length(),
                    e.toString());
            return null;
        }
    }

    /** 解析成对象；不是 JSON 对象时返回空表。 */
    static Map<String, Object> object(String text) {
        return asObject(parse(text));
    }

    /**
     * 一个值如果是 Map 就归一成 {@code Map<String,Object>}（Jackson 给的是 {@code Map<Object,Object>}）。
     * 成功与失败两条路都返回**可变**容器，调用方不必区分返回值能不能改。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** 一个值如果是数组就归一成 {@code List<Object>}；标量返回空表（调用方按"没有多值"处理）。 */
    static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        return List.of();
    }

    /** 数组里的每一项都归一成对象（非对象的项被丢掉）。 */
    static List<Map<String, Object>> asObjectList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : asList(value)) {
            if (element instanceof Map<?, ?>) {
                result.add(asObject(element));
            }
        }
        return result;
    }

    /**
     * jsonb 数组里的 id 列表；元素可能是数字也可能是字符串，统一成字符串按原序返回。
     *
     * <p>非标量元素（对象 / 数组）与 trim 之后为空的元素一律跳过：前者会变成 {@code {a=1}}
     * 这种无意义的"id"，后者会让消费方（id 集合、sitemap）产生无效引用。
     */
    static List<String> asTextList(Object value) {
        List<String> result = new ArrayList<>();
        for (Object element : asList(value)) {
            if (element == null || element instanceof Map<?, ?> || element instanceof List<?>) {
                continue;
            }
            String text = String.valueOf(element).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    /** 一个值是否是 JSON 数组 / 对象（用于判断"这个字段有没有值"）。 */
    static boolean isComposite(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }
}
