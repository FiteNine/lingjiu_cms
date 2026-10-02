package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面计划的一项（static-publish.md §8.2）。一个实例 = 一个产物文件。
 *
 * <p>计划生成是**纯读**：不写库、不写盘、不改任何状态，因此可以只算计划（预演）。
 *
 * <p>{@link #scope} 是"这一页的页面上下文"：渲染前由 {@code PageRenderer} 逐项压进
 * {@code RenderContext} 的具名作用域（{@code channel} / {@code param}），以及挂在这条计划上的
 * 数据对象（{@code entry} 当前内容、{@code windowOption} 归档窗口）。把它放在计划里而不是让
 * 渲染器重新算一遍，是为了让"预演"看到的东西与"真发布"渲染的东西**逐项相同**。
 */
public record Plan(PageType pageType, String url, String path, String template, String typeCode,
                   SourceRef source, int pageNo, PageUrlBuilder pageUrls, ContentItem entry,
                   ContentQuery listQuery, Map<String, Object> scope, String title,
                   PageType sourcePageType) {

    public Plan {
        // Map.copyOf 对 null 键/值零容忍：scope 是任意 Map，混进一个 null 值就会在**计划生成阶段**
        // 抛一个没有上下文的 NPE，把整站发布打断。这里显式过滤（与 SiteConfig 同一口径）。
        if (scope == null) {
            scope = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            scope.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            scope = java.util.Collections.unmodifiableMap(copy);
        }
        // 绝大多数页面"来源 = 自己"；只有派生页会显式传一个不同的来源
        sourcePageType = sourcePageType == null ? pageType : sourcePageType;
    }

    /**
     * 这一页**来源**的页面类型（派生页传第 1 页的类型，见 {@code PageRenderer} 的用法）。
     *
     * <p>{@link #pageType} 回答"这一页算哪一类页面"（决定 noindex、sitemap、§6.7 矩阵归属），
     * 而 {@code sourcePageType} 回答"它继承谁的语义"（决定 §7.2.1 的分类索引页缺省）。
     * 两者只在派生页上不同：{@code page-2/} 是 {@code DPAGE}，但它是**列表页的第 2 页**。
     *
     * <p>构造器已经把 null 归一成 {@code pageType}，这里直接返回字段（record 自动生成的访问器
     * 与原先手写的空判分支完全等价）。
     */
    public PageType sourcePageType() {
        return sourcePageType;
    }

    /** 数据来源（§8.2 的 {@code sourceRef}）；用于依赖清单与发布日志。 */
    public record SourceRef(String kind, Object id, String code) {

        public static SourceRef home() {
            return new SourceRef("home", 0L, null);
        }

        public static SourceRef content(long id, String typeCode) {
            return new SourceRef("content", id, typeCode);
        }

        public static SourceRef category(long id, String slug) {
            return new SourceRef("category", id, slug);
        }

        public static SourceRef type(String typeCode) {
            return new SourceRef("type", 0L, typeCode);
        }

        public static SourceRef tag(String slug) {
            return new SourceRef("tag", 0L, slug);
        }

        public static SourceRef archive(String yearMonth) {
            return new SourceRef("archive", 0L, yearMonth);
        }

        public static SourceRef facet(String facetPath) {
            return new SourceRef("facet", 0L, facetPath);
        }

        public static SourceRef staticPage(String code) {
            return new SourceRef("static", 0L, code);
        }

        public static SourceRef special(String kind) {
            return new SourceRef(kind, 0L, null);
        }

        /** 发布日志里的一行描述。 */
        public String describe() {
            return code == null ? kind : kind + ":" + code;
        }
    }

    /* ---------------- 便捷取值 ---------------- */

    /** 该页的具名 {@code channel} 作用域（没有则空表）。 */
    public Map<String, Object> channel() {
        Object value = scope.get("channel");
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> channel = new LinkedHashMap<>();
        map.forEach((key, item) -> channel.put(String.valueOf(key), item));
        return channel;
    }

    /** 该页注入的 {@code param} 作用域（§7.2.3 的四个 key）。 */
    public Map<String, Object> params() {
        Object value = scope.get("param");
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        map.forEach((key, item) -> params.put(String.valueOf(key), item));
        return params;
    }

    /** 依赖到的内容 id（sitemap 的 lastmod 与 §8.4 的依赖清单都用它）。 */
    public List<Long> contentIds() {
        Object value = scope.get("contentIds");
        if (value instanceof List<?> list) {
            return list.stream().filter(Number.class::isInstance).map(item -> ((Number) item).longValue())
                    .toList();
        }
        return List.of();
    }

    public long parentId() {
        return entry == null ? 0L : entry.parentId();
    }
}
