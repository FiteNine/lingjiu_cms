package com.lingjiuw.cms.module.cms.publish.model;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一内容项（static-publish.md §4.1）：内置列 + 自定义字段值 + 派生字段，一个 Map 装完。
 *
 * <p><b>为什么是 Map 而不是 POJO</b>：字段是站点自定义的（19 种字段类型 × 任意 code），
 * 引擎必须在不知道业务字段名的前提下遍历、判定路径、输出——Map 是这套设计里唯一说得通的表示。
 * 强类型的部分（{@code id} / {@code typeCode} / {@code slug} / {@code title}）另给访问器，
 * 因为它们是引擎自己要用的，不是给模板用的。
 *
 * <p><b>谁负责填哪些 key</b>：数据层（{@code ContentProvider} 的实现）负责内置列、自定义字段、
 * 以及**不依赖页面上下文**的派生字段（{@code typeCode} / {@code parentTitle} / {@code url} /
 * {@code canonical} / {@code tags} / {@code toc}…）；**依赖页面上下文**的两个派生字段
 * {@code current} 与 {@code class}（§5.5）由渲染层在压栈时补，因为它们要跟"这一页是谁"比对。
 *
 * <p>值允许为 {@code null}（例如 {@code summary} 没填），因此这里**不能**用
 * {@link Map#copyOf}——它遇到 null 会抛 NPE。
 */
@Slf4j
public final class ContentItem {

    private final Map<String, Object> values;

    private ContentItem(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** 包一层 Map；调用方传进来的表会被复制。 */
    public static ContentItem of(Map<String, Object> values) {
        return new ContentItem(values);
    }

    /** 空内容项（单页类型还没建内容时用它占位，避免到处判空）。 */
    public static ContentItem empty() {
        return new ContentItem(new LinkedHashMap<>());
    }

    public Map<String, Object> values() {
        return values;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public long id() {
        return longOf(values.get("id"));
    }

    public String typeCode() {
        Object value = values.get("typeCode");
        return value == null ? null : String.valueOf(value);
    }

    public String typeName() {
        Object value = values.get("typeName");
        return value == null ? null : String.valueOf(value);
    }

    public String slug() {
        Object value = values.get("slug");
        return value == null ? null : String.valueOf(value);
    }

    public String title() {
        Object value = values.get("title");
        return value == null ? null : String.valueOf(value);
    }

    /** 详情页 URL；数据层用 {@code UrlResolver} 算好放进来（§6.3："模板里禁止裸拼 .html"）。 */
    public String url() {
        Object value = values.get("url");
        return value == null ? null : String.valueOf(value);
    }

    public long parentId() {
        return longOf(values.get("parentId"));
    }

    /** 复制一份并可写，供渲染层补 {@code current} / {@code class}（§5.5）。 */
    public Map<String, Object> mutableValues() {
        return new LinkedHashMap<>(values);
    }

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().trim());
            } catch (NumberFormatException e) {
                // 脏数据会被当成 id=0 / 无父级，而 0 恰好是"没有"的合法取值：
                // 记一条日志，别让"内容按 id 关联不上、父级找不到"变成一个查不出原因的现象。
                log.warn("ContentItem 的数值字段解析失败，按 0 处理：'{}'", text);
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public String toString() {
        return "ContentItem(" + typeCode() + " #" + id() + " " + title() + ")";
    }
}
