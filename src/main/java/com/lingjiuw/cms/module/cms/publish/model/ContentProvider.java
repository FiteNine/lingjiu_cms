package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;

/**
 * 引擎的唯一取数出口（static-publish.md §4.1："取数接口：唯一实现在 {@code cms_content}"）。
 *
 * <p>标签实现**只依赖这个接口**，因此引擎内核的测试可以用内存实现跑完（§2.6 期 1：
 * "建 {@code cms_content}…；标签实现只依赖 {@code ContentProvider} 接口"）。
 *
 * <p><b>一条跨实现的口径</b>：所有计数（{@code channel.count} / {@code tagnav.count} /
 * {@code archive.count} / facet 的 {@code count>0} / 作者文章数 / {@code {cms:prenext}} 的邻接
 * 判定）与列表查询必须走**同一个判定函数**——"{@code PUBLISHED} ∧ {@code publish_time ≤ now()}
 * ∧ 未到期"（§6.3）。因此本接口把这些能力放在同一处，而不是让每个标签各写一遍过滤条件。
 */
public interface ContentProvider {

    /** 当前站点 id。 */
    long siteId();

    /** 站点配置与发布选项（§2.7）。 */
    SiteConfig site();

    /* ---------------- 定义（编译期校验用） ---------------- */

    /** 本站点的全部内容类型定义。 */
    List<ContentTypeDef> types();

    /** 按 code 取类型定义；不存在返回 null（调用方报 E2006）。 */
    ContentTypeDef type(String typeCode);

    /** 按 code 取表单定义；不存在返回 null（调用方报 E2008）。 */
    FormDef form(String code);

    /** 本站点全部表单 code，供 E2008 的"→ 建议"列清单。 */
    List<String> formCodes();

    /** 本站点全部菜单 code，供 E2006 的"→ 建议"列清单。 */
    List<String> menuCodes();

    /** 分类是否存在（参数可为 id 或 slug），用于 §4.5 第 7 条。 */
    boolean categoryExists(String idOrSlug);

    /** 标签是否存在（slug），用于 §4.5 第 7 条。 */
    boolean tagExists(String slug);

    /** 作者是否存在（id 或 slug；{@code self} 不在此校验），用于 §4.5 第 7 条。 */
    boolean authorExists(String idOrSlug);

    /* ---------------- 单条内容 ---------------- */

    /** 按类型 + id 取一条内容；不存在返回 null。 */
    ContentItem content(String typeCode, long id);

    /** 跨类型按 id 取一条内容（{@code content_by_slug} 之外的唯一入口，供 {@code RELATION} 用）。 */
    ContentItem contentById(long id);

    /** 按类型 + slug 取一条内容；不存在返回 null。 */
    ContentItem contentBySlug(String typeCode, String slug);

    /* ---------------- 列表查询 ---------------- */

    /** 执行一次查询，返回当页的行与总数。{@code row=0} 表示不分页（取全部）。 */
    QueryResult query(ContentQuery query);

    /** 只取总数，判定口径与 {@link #query} 完全一致（§6.3）。 */
    long count(ContentQuery query);

    /** 层级内容的直接子项（{@code of='self'} 与目录树都用它）。 */
    List<ContentItem> children(long parentId, String typeCode, int depth);

    /** 内容自身的祖先链（{@code ancestors} 派生字段与面包屑的内容层）。 */
    List<ContentItem> contentAncestors(long contentId);

    /* ---------------- 导航来源（§6.4） ---------------- */

    /** 分类树；{@code parentId} 为 null 表示从顶级起。 */
    List<NavItem> categories(Long parentId, int depth, CountScope countScope);

    /** 按 id 或 slug 取一个分类节点；不存在返回 null。 */
    NavItem category(String idOrSlug);

    /** 分类的祖先链（面包屑从顶到下）。 */
    List<NavItem> categoryAncestors(long categoryId);

    /** 类型列表（{@code source='type'}：行业站"六个类型 = 六个导航项"）。 */
    List<NavItem> typeNodes();

    /** 菜单项（{@code source='menu'}）。 */
    List<NavItem> menu(String code);

    /** 某类型的某个 facet 字段的取值条（{@code source='facet'}，§7.4）。 */
    List<NavItem> facetValues(String typeCode, String fieldCode);

    /* ---------------- 标签与归档（§6.4） ---------------- */

    /**
     * 标签与其内容数。
     *
     * @param typeCode 只统计该类型；{@code null} / {@code all} 表示全类型合并（跨类型同名标签按 slug 合并）
     * @param orderby  {@code count} / {@code name} / {@code sort}
     */
    List<NavItem> tags(String typeCode, int row, String orderby, int minCount);

    /** 按 slug 取一个标签节点；不存在返回 null。 */
    NavItem tag(String slug);

    /**
     * 年月归档。
     *
     * @param mode   {@code year} 或 {@code month}
     * @param category 限定栏目，{@code all} / null 不限
     */
    List<NavItem> archives(String typeCode, String mode, int row, String category, boolean includeChildren);

    /* ---------------- 邻接（{@code {cms:prenext}}） ---------------- */

    /**
     * 同类型相邻内容（§6.4）。
     *
     * @param within   {@code type} 全类型内相邻 / {@code parent} 仅同一父内容内相邻
     * @param category 限定同一分类；{@code all} / null 不限
     */
    Neighbors neighbors(String typeCode, long anchorId, String within, String category);

    /* ---------------- 结果类型 ---------------- */

    /**
     * 一次查询的结果。
     *
     * @param rows       当页的行（已按 §6.3 排序，末尾带 {@code id desc} 兜底）
     * @param totalCount 满足条件的总条数（同一判定函数）
     * @param pageSize   当页条数上限
     */
    record QueryResult(List<ContentItem> rows, long totalCount, int pageSize) {

        public QueryResult {
            rows = rows == null ? List.of() : List.copyOf(rows);
            // 三条不变式早报比晚报便宜：它们一旦被破坏，症状是"分页重复/丢条"或"sitemap 少页"，
            // 而那时候已经离出错的数据层很远了（§6.3 要求计数与列表同一判定口径）。
            if (pageSize < 0) {
                throw new IllegalArgumentException("QueryResult 的 pageSize 不能为负：" + pageSize);
            }
            if (pageSize > 0 && rows.size() > pageSize) {
                throw new IllegalArgumentException("QueryResult 的当页行数 " + rows.size()
                        + " 超过了 pageSize=" + pageSize + "（分页语义被破坏）");
            }
            if (totalCount < rows.size()) {
                throw new IllegalArgumentException("QueryResult 的 totalCount=" + totalCount
                        + " 小于当页行数 " + rows.size() + "（计数与列表不是同一个判定口径）");
            }
        }

        public boolean empty() {
            return rows.isEmpty();
        }
    }

    /** 上一篇 / 下一篇；不存在的那一侧为 null（§6.4："不存在时不渲染该次迭代"）。 */
    record Neighbors(ContentItem prev, ContentItem next) {

        public static final Neighbors NONE = new Neighbors(null, null);
    }

    /** {@code {cms:channel}} 的 {@code countScope}：内容数是否含子分类（§6.4）。 */
    enum CountScope {
        /** 只算本节点自己的内容数。 */
        node,
        /** 含子分类（默认）。 */
        tree
    }
}
