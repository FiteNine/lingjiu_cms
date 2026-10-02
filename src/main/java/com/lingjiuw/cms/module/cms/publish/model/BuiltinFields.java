package com.lingjiuw.cms.module.cms.publish.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 引擎侧的**封闭清单**（static-publish.md §2.2、§2.7、§5.1、§5.6、§6.3、§7.2.3）。
 *
 * <p>这些清单在契约里被反复引用，而"省略号是承重的"（§2.7 原话）——只要有一处用 {@code …}
 * 收尾，"找不到即报错"就会变成"找不到就报错，但没人知道该有哪些"。因此它们必须落在代码里，
 * 而且**只有这一份**：§4.5 的第 4/5/6/7 条校验、{@code {cms:if}} 的字段名、报错文案里的
 * "可用字段有 …"，全都读这里。
 */
public final class BuiltinFields {

    private BuiltinFields() {
    }

    /** 内置字段（§2.2，21 个）：{@code cms_content} 上有真实列，无需声明即可用。 */
    public static final Set<String> BUILTIN = Set.of(
            "id", "title", "slug", "summary", "cover", "status", "publishTime", "expireTime",
            "createTime", "updateTime", "sort", "top", "recommend", "authorId", "authorName",
            "viewCount", "seoTitle", "seoDescription", "seoKeywords", "contentFormat", "parentId");

    /** 派生字段（§2.2）：引擎维护，任何类型都有，模板直接可用。 */
    public static final Set<String> DERIVED = Set.of(
            "typeCode", "typeName", "url", "canonical",
            "parentTitle", "parentSlug", "parentUrl", "parentTypeCode", "ancestors",
            "childCount", "hasChildren",
            "categoryId", "categoryName", "categoryPath", "categoryUrl", "categories", "tags",
            "authorUrl", "authorAvatar",
            "wordCount", "readingMinutes", "imageCount", "toc", "updatedDaysAgo",
            "viewCountDay", "viewCountWeek", "commentCount", "ratingAvg", "ratingCount",
            "current", "class");

    /** 正文字段（§2.3、§5.2.2）：正文与预渲染结果，两类正文都以 {@code content_html} 为唯一渲染源。 */
    public static final Set<String> BODY = Set.of("content", "contentHtml");

    /** {@code {cms:detail}} 的专用产出（§6.3）：上下篇是否存在、正文分页信息。 */
    public static final Set<String> DETAIL_ONLY = Set.of("hasPrev", "hasNext", "pageNo", "totalPages");

    /**
     * {@code type='all'} 时迭代项可用的字段（§6.3 v2.2 定死）。
     *
     * <p>契约的原文是"公共列 + 不依赖类型的派生字段（…）"，那个省略号同样是承重的。
     * 这里落地为：**内置列 ∪ 派生字段 ∪ 正文字段**，即"除自定义字段之外的全部"——
     * 因为自定义字段是唯一真正依赖类型定义的东西（§2.3："查询与 URL 只依赖这些通用列 + 索引表"）。
     */
    public static final Set<String> CROSS_TYPE;

    /** {@code where} 可用的字段（§2.2 白名单，19 个）；值一律用 id（§2.5）。 */
    public static final Set<String> WHERE_CAPABLE = Set.of(
            "id", "slug", "typeCode", "status", "publishTime", "expireTime", "updateTime", "sort",
            "top", "recommend", "authorId", "parentId", "viewCount", "viewCountDay", "viewCountWeek",
            "commentCount", "wordCount", "categoryId", "tagId");

    /** {@code orderby} 可用的字段（§2.2 白名单，11 个）。 */
    public static final Set<String> ORDERBY_CAPABLE = Set.of(
            "publishTime", "updateTime", "sort", "viewCount", "viewCountDay", "viewCountWeek",
            "commentCount", "wordCount", "id", "top", "recommend");

    /** {@code orderby} 的保留值（§6.3）：只在 {@code relate='field:<code>'} 时可用。 */
    public static final String RELATION_ORDER = "relationOrder";

    /**
     * {@code site} 作用域的 key（§2.7、§5.1 第（2）条）：22 个闭 key + 一个开放前缀 {@code option}。
     *
     * <p>{@code option} 是"开放前缀"：{@code [field:site.option.<code>/]} 的第三段起是发布选项表
     * 里的数据（{@code contact.wechat}、{@code legal.orgName}…），编译期不穷举
     * （{@link com.lingjiuw.cms.module.cms.publish.template.validate.FieldPathValidator}
     * 的具名作用域校验只看前两段，所以加进这个集合就等于放行整棵子树）。
     */
    public static final Set<String> SITE_KEYS = Set.of(
            "id", "name", "code", "domain", "url", "protocol", "logo", "lang", "description",
            "keywords", "seoDescription", "icp", "contactPhone", "contactEmail", "rootDir",
            "defaultCover", "ogImage", "theme", "statisticsCode", "mediaHost", "year", "alternates",
            "option");

    /** {@code page} 作用域的 key（§5.6）：前 6 个恒有值，其余只在有分页主体时有值。 */
    public static final Set<String> PAGE_KEYS = Set.of(
            "url", "title", "canonical", "noindex", "robots", "lastmod",
            "pageNo", "totalPages", "totalCount", "pageSize", "pageType", "paginationKind",
            "isFirst", "isLast", "empty", "hasResults", "hasPrev", "hasNext",
            "currentUrl", "firstUrl", "prevUrl", "nextUrl", "lastUrl",
            // 归档页的年月（页面计划填；与 channel.year / channel.month 同源）
            "year", "month");

    /** {@code query.<name>} 的 key（§5.4、§6.3）：结果元信息，外加可下标取的结果集。 */
    public static final Set<String> QUERY_KEYS = Set.of("empty", "hasResults", "totalCount", "pageSize", "rows");

    /**
     * {@code param} 作用域里由引擎注入的 4 个 key（§7.2.3，**全站唯一的豁免，且只用于回显**）。
     * 全站只有这 4 个，注入的值一律按模板文本转义后输出。
     */
    public static final Set<String> INJECTED_PARAM_KEYS = Set.of("q", "page", "form_error", "form_ok");

    /** 分类来源的 {@code channel}（§5.1 第（4）条第一行）：分类的 key，**没有** {@code typeCode}。 */
    private static final Set<String> CATEGORY_CHANNEL_KEYS =
            Set.of("id", "name", "label", "slug", "url", "path", "count");

    /**
     * {@code channel} 的 key 清单（§5.1 第（4）条），按页面类型分支。
     *
     * <p>{@code LIST} 有两种来源（分类索引页 / 类型列表页，§7.2.1 定死），而本方法的入参
     * 只有页面类型——分不出来源时把两套 key 都放行：少给一个 key 会让"按 §5.1 写的模板"
     * 被判 E1004（合法模板被引擎拒绝），比多放行一个 key 严重得多。
     *
     * <p>详情页 / 单页的 {@code channel} 是**主分类**（§5.1 v2.2 明确），所以走分类那套 key：
     * 含 {@code path}、不含 {@code typeCode}。
     */
    public static Set<String> channelKeys(PageType pageType, Long categoryId) {
        return switch (pageType) {
            case LIST -> categoryId == null
                    ? Set.of("id", "name", "label", "slug", "url", "path", "typeCode", "count")
                    : CATEGORY_CHANNEL_KEYS;
            case DETAIL, DPAGE, SINGLE -> CATEGORY_CHANNEL_KEYS;
            case TAGPAGE -> Set.of("id", "name", "label", "slug", "url", "count");
            case ARCHIVE -> Set.of("year", "month", "label", "url", "count");
            case FACET -> Set.of("label", "slug", "url", "facetPath", "count");
            default -> Set.of();
        };
    }

    /** 全部可迭代的字段来源（§3.5 裁定五 + §6.2 的 {@code foreach} 清单）。 */
    public static final List<String> FOREACH_SOURCES = List.of(
            "images", "files", "related", "tags", "specs", "children", "toc", "categories",
            "ancestors", "site.alternates");

    /** §6.1 的 14 个标签名（一个不多）。 */
    public static final List<String> TAG_NAMES = List.of(
            "include", "if", "else", "foreach",
            "list", "query", "detail",
            "pagelist", "channel", "breadcrumb", "prenext", "tagnav", "archive",
            "form");

    /** 6 个保留名（§5.1）：不得用作类型 code、字段 code 或 include 参数名。 */
    public static final List<String> RESERVED_NAMES = List.of(
            "site", "channel", "page", "param", "query", "item");

    static {
        Set<String> cross = new LinkedHashSet<>();
        cross.addAll(BUILTIN);
        cross.addAll(DERIVED);
        cross.addAll(BODY);
        cross.addAll(DETAIL_ONLY);
        CROSS_TYPE = Set.copyOf(cross);
    }

    /** 字段 code 是否落在"可筛选"白名单里。 */
    public static boolean whereCapable(String code) {
        return WHERE_CAPABLE.contains(code);
    }

    /** 字段 code 是否落在"可排序"白名单里。 */
    public static boolean orderbyCapable(String code) {
        return ORDERBY_CAPABLE.contains(code);
    }

    /* ---------------- 内置字段与派生字段的字段声明（§2.2 的下限） ---------------- */

    /**
     * 内置字段、派生字段、正文字段与迭代项通用字段的**字段声明**。
     *
     * <p>为什么必须有这张表：渲染期有三件事只能靠字段声明做出来，而它们**连自定义字段都不是**——
     * <ul>
     *   <li>§5.2.1 的 {@code raw}：{@code [field:content/]} 是富文本，必须原样输出。没有这张表，
     *       正文会被转义成 {@code &lt;p&gt;}，模板作者还无权关闭转义（这正是设计要的效果）；</li>
     *   <li>§2.2 的默认输出与 formatter：{@code [field:publishTime format='Y-m-d'/]} 要知道这是
     *       日期、{@code [field:cover/]} 要知道这该输出 url、{@code summary} 要知道换行转 {@code <br>}；</li>
     *   <li>§2.2 第 4 条的组合合法性：{@code size} 用在 {@code TEXT} 上要报 E1002，得先知道它是 {@code TEXT}。</li>
     * </ul>
     *
     * <p>自定义字段的声明来自 {@code cms_field}（由数据层按类型给出，经
     * {@code RenderContext.fieldDefLookup} 注入）；这张表是**兜底**，覆盖"没有类型定义的字段"：
     * 内置列、派生字段、正文字段、以及迭代项里的通用字段（{@code index} / {@code level} / {@code count}…）。
     *
     * <p>与 {@link #BUILTIN} / {@link #DERIVED} 的分工：那两个集合回答"这个名字能不能用"（编译期校验），
     * 这张表回答"用的时候按什么类型处理"（渲染期输出）。两者必须同时存在——只答前者会让正文被转义。
     */
    public static final Map<String, FieldDef> BUILTIN_DEFS;

    /** 取一个内置/派生字段的声明；没有这张表里的条目时返回 null（调用方按"声明未知"处理）。 */
    public static FieldDef builtinDef(String code) {
        return BUILTIN_DEFS.get(code);
    }

    private static void def(Map<String, FieldDef> map, String code, FieldType type, boolean raw) {
        map.put(code, FieldDef.builder(null, code, type).raw(raw).build());
    }

    private static void def(Map<String, FieldDef> map, String code, FieldType type) {
        def(map, code, type, false);
    }

    static {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        // 正文字段：入库时已清洗 / 预渲染，字段标为 raw（§5.2.1、§5.2.2）
        def(defs, "content", FieldType.RICHTEXT, true);
        def(defs, "contentHtml", FieldType.RICHTEXT, true);
        // 文本
        for (String code : new String[]{"title", "slug", "authorName", "seoTitle", "seoKeywords",
                "typeCode", "typeName", "url", "canonical", "parentTitle", "parentSlug", "parentUrl",
                "parentTypeCode", "categoryName", "categoryPath", "categoryUrl", "authorUrl",
                "authorAvatar", "class", "contentFormat", "status",
                // 迭代项的通用字段（§6.2 / §6.4 的产出）
                "name", "label", "alt", "text", "key", "value", "mime", "ext", "dimension", "lang",
                "target", "rel", "facetPath", "type", "contentType"}) {
            def(defs, code, FieldType.TEXT);
        }
        // 多行文本：默认输出"转义文本 + <br>"
        def(defs, "summary", FieldType.TEXTAREA);
        def(defs, "seoDescription", FieldType.TEXTAREA);
        def(defs, "description", FieldType.TEXTAREA);
        // 图片：默认输出 url，可 size='thumb|medium|large'
        def(defs, "cover", FieldType.IMAGE);
        def(defs, "ogImage", FieldType.IMAGE);
        def(defs, "defaultCover", FieldType.IMAGE);
        def(defs, "logo", FieldType.IMAGE);
        // 时间
        for (String code : new String[]{"publishTime", "expireTime", "createTime", "updateTime", "lastmod"}) {
            def(defs, code, FieldType.DATETIME);
        }
        // 整数
        for (String code : new String[]{"id", "sort", "authorId", "viewCount", "viewCountDay",
                "viewCountWeek", "commentCount", "ratingCount", "parentId", "childCount", "categoryId",
                "wordCount", "readingMinutes", "imageCount", "updatedDaysAgo", "pageNo", "totalPages",
                "index", "index0", "count", "size", "width", "height", "year", "month", "totalCount",
                "pageSize", "childTotal"}) {
            def(defs, code, FieldType.INT);
        }
        // 小数
        def(defs, "ratingAvg", FieldType.DECIMAL);
        // 布尔：`{cms:if field='x'}` 直接判真假，也可以 show='class'
        for (String code : new String[]{"top", "recommend", "hasChildren", "current", "isFirst",
                "isLast", "hasPrev", "hasNext", "empty", "hasResults", "noindex", "visible", "ok"}) {
            def(defs, code, FieldType.BOOL);
        }
        // 多值：可 {cms:foreach} 迭代、可取 .count
        def(defs, "tags", FieldType.TAGS);
        def(defs, "images", FieldType.IMAGES);
        def(defs, "files", FieldType.FILES);
        def(defs, "related", FieldType.RELATION);
        def(defs, "children", FieldType.JSON);
        def(defs, "specs", FieldType.JSON);
        def(defs, "toc", FieldType.JSON);
        def(defs, "ancestors", FieldType.JSON);
        def(defs, "categories", FieldType.JSON);
        def(defs, "alternates", FieldType.JSON);
        def(defs, "rows", FieldType.JSON);
        BUILTIN_DEFS = Map.copyOf(defs);
    }
}
