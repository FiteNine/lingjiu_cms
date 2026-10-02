package com.lingjiuw.cms.module.cms.publish.provider;

import lombok.Data;

/**
 * 各种"分组计数"的行类型（XML 的 {@code resultType} 用 {@code 本类$内部类} 的写法，见 backend/AGENTS.md）。
 *
 * <p>它们都很小，散成 7 个文件只会让"数据层到底查了哪些聚合"更难读，因此集中在这里。
 */
public final class Rows {

    private Rows() {
    }

    /** 一个分类下已发布内容的条数（{@code CountScope.node} 口径）。 */
    @Data
    public static class CategoryCountRow {
        private Long categoryId;
        private Long count;
    }

    /** 一个标签的内容数与名字（{@code {cms:tagnav}} 的取数）。 */
    @Data
    public static class TagCountRow {
        private Long id;
        private String name;
        private String slug;
        private Long count;
    }

    /** 一个内容类型下已发布内容的条数（{@code {cms:channel source='type'}} 的 {@code count}）。 */
    @Data
    public static class TypeCountRow {
        private String typeCode;
        private Long count;
    }

    /** 一个年月（或年份）分组的内容数（{@code {cms:archive}} 的取数）。 */
    @Data
    public static class ArchiveCountRow {
        private Integer year;
        private Integer month;
        private Long count;
    }

    /** 一个 facet 取值的条数（{@code {cms:channel source='facet'}} 的取数）。 */
    @Data
    public static class FacetCountRow {
        private String value;
        private Long count;
    }

    /** 一个父内容下的直接子内容数（{@code childCount} 派生字段）。 */
    @Data
    public static class ChildCountRow {
        private Long parentId;
        private Long count;
    }

    /**
     * 内容与分类 / 标签的关联行（{@code cms_content_category} / {@code cms_content_tag}）。
     * 批量取一页内容的关联，避免逐条 SQL。
     */
    @Data
    public static class RelationRow {
        private Long contentId;
        private Long refId;
        private String dimension;
    }
}
