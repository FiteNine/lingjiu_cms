package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;
import java.util.Map;

/**
 * 内容类型定义（static-publish.md §2.1 的 {@code cms_content_type}）。
 *
 * <p>"小说站 = {@code book} + {@code chapter}；行业站 = {@code product} + {@code case} + {@code service}"
 * ——站点自助定义类型，引擎按定义决定页面形态、URL 规则与模板。
 */
public record ContentTypeDef(long id, String code, String name, Kind kind, boolean hierarchical,
                             String detailUrlPattern, String listUrlPattern,
                             String detailTemplate, String listTemplate, String paginateBody,
                             String sortField, String sortOrder, int perPage,
                             String seoTitleField, String seoDescField,
                             List<FieldDef> fields, Map<String, Object> options) {

    /** 类型的形态（§2.1）。 */
    public enum Kind {
        /** 有列表 + 详情。 */
        CONTENT,
        /** 单页：全站仅一份、不参与列表（"关于我们"）。 */
        SINGLE,
        /** 层级内容（"书→章"、"系列→篇"）。 */
        TREE
    }

    public ContentTypeDef {
        fields = fields == null ? List.of() : List.copyOf(fields);
        // Map.copyOf 对 null 值零容忍，而 options 来自 cms_content_type.options 的 JSON
        // （{"foo": null} 是合法 JSON）：过滤掉 null 值，别让一条脏选项把整站发布打断。
        if (options == null) {
            options = Map.of();
        } else {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            options.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            options = java.util.Collections.unmodifiableMap(copy);
        }
    }

    /** 不带字段定义的类型骨架，供测试与计划期使用。 */
    public static ContentTypeDef of(long id, String code, String name, Kind kind) {
        return new ContentTypeDef(id, code, name, kind, kind == Kind.TREE,
                "/" + code + "/{slug}.html", null, null, null, "content",
                "publishTime", "desc", 20, null, null, List.of(), Map.of());
    }

    /** 按字段 code 取字段定义；没有则返回 null。 */
    public FieldDef field(String fieldCode) {
        for (FieldDef field : fields) {
            // Objects.equals：FieldDef 的 code 允许为 null（字段定义来自 cms_field 行映射）
            if (java.util.Objects.equals(field.code(), fieldCode)) {
                return field;
            }
        }
        return null;
    }

    /** 该类型是否走正文分页（{@code paginate_body} 非空，§2.1）。 */
    public boolean paginatesBody() {
        return paginateBody != null && !paginateBody.isBlank();
    }

    /** 类型的默认每页条数；未配时取 20。 */
    public int perPageOrDefault() {
        return perPage > 0 ? perPage : 20;
    }

    /** 默认排序字段；未配时按 §6.3 退回 {@code publishTime desc}。 */
    public String sortFieldOrDefault() {
        return sortField == null || sortField.isBlank() ? "publishTime" : sortField;
    }
}
