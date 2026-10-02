package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;

/**
 * 页面类型（static-publish.md §7.2.1 的 11 种，加两处必须区分但矩阵未单列的取值）。
 *
 * <p>为什么比 §7.2.1 多两个：§6.7 的"标签 × 页面类型"矩阵列了 11 列
 * （HOME / LIST / TAGPAGE / ARCHIVE / DETAIL / SINGLE / FACET / SEARCH / STATIC / 404 / feed），
 * 而 §7.2.1 与 §13.1 的清单里还有 {@code TAGLIST}（标签总览页）与 {@code DPAGE}（第 2..N 页）。
 * 两者都不能塞进矩阵现有的列而不丢语义，因此本枚举收敛为 13 个取值，并用
 * {@link #matrixColumn()} 把矩阵之外的取值映射回它所属的列——**规则只有一条，不改矩阵**。
 */
public enum PageType {

    /** 首页：固定 `/`，栈底无当前条目。 */
    HOME,

    /** 列表页：类型来源（类型的 `list_url_pattern`）或分类来源（站点选项 `url.list`）。 */
    LIST,

    /** 标签总览页：`url.tags`，无分页主体。矩阵里归入 {@link #LIST} 列。 */
    TAGLIST,

    /** 标签详情页：`url.tag`。 */
    TAGPAGE,

    /** 归档页：`url.archive`。 */
    ARCHIVE,

    /** 内容详情页（可承载可分页列表）。 */
    DETAIL,

    /** 详情页的第 2..N 页：与 {@link #DETAIL} 同模板、同上下文，只有 `page.pageNo` 不同。 */
    DPAGE,

    /** 单页：每个 SINGLE 类型一个。 */
    SINGLE,

    /** 筛选落地页。 */
    FACET,

    /** 搜索页壳（H 类）：没有"浏览位置"。 */
    SEARCH,

    /** 站点声明的静态页（感谢页 / 联系我们 / 站点声明的列表页）。 */
    STATIC,

    /** 404 页：只有 `site` 作用域与当前页信息。 */
    PAGE404,

    /** feed：只允许一个顶层 `{cms:query}`，不提供 `page` / `channel`。 */
    FEED;

    /**
     * 该页面类型在 §6.7 矩阵里的列。
     *
     * <p>{@link #DPAGE} 归 {@link #DETAIL}（§7.2.1：同一个模板、同一份上下文）。
     *
     * <p><b>{@link #TAGLIST} 不映射</b>——它没有列可归：§7.2.1 明写 TAGLIST 的"分页主体 = 无"，
     * 而归入 LIST 会让 {@code {cms:list}} 变成合法（LIST 列允许 0–1）。不映射的结果是各标签按自己的
     * {@code default} 分支处理 TAGLIST，而那一支恰好就是裁定要的答案：
     * <ul>
     *   <li>{@code {cms:list}} / {@code {cms:pagelist}} / {@code {cms:detail}} / {@code {cms:prenext}}
     *       → 0（它们的分页主体语义在 TAGLIST 上不成立）；</li>
     *   <li>{@code {cms:channel}} / {@code {cms:breadcrumb}} / {@code {cms:query}} / {@code {cms:tagnav}}
     *       / {@code {cms:archive}} / {@code {cms:form}} → 不限（与 LIST 列一致）。</li>
     * </ul>
     */
    public PageType matrixColumn() {
        return this == DPAGE ? DETAIL : this;
    }

    /**
     * 该页面类型上是否存在具名作用域 {@code channel}（§5.1 第（4）条那张表，v2.2 定稿）。
     * 首页 / 搜索页 / 404 / 静态页都没有"浏览位置"。
     */
    public boolean hasChannel() {
        return switch (this) {
            case LIST, TAGPAGE, ARCHIVE, FACET, DETAIL, SINGLE, DPAGE -> true;
            case HOME, TAGLIST, SEARCH, STATIC, PAGE404, FEED -> false;
        };
    }

    /** 该页面类型是否有"当前条目"压在匿名栈底（§5.1 第（1）条）。 */
    public boolean hasCurrentEntry() {
        return this == DETAIL || this == DPAGE || this == SINGLE;
    }

    /** 该页面类型是否有分页主体（可用于判断 {@code page} 的分页字段是否有值）。 */
    public boolean paginatable() {
        return switch (this) {
            case HOME, LIST, TAGPAGE, ARCHIVE, DETAIL, DPAGE, FACET, STATIC -> true;
            case TAGLIST, SINGLE, SEARCH, PAGE404, FEED -> false;
        };
    }

    /** §5.6 的 {@code page.pageType}：分页主体的页面类型只有这三种取值。 */
    public String pageTypeCode() {
        return switch (this) {
            case HOME -> "HOME";
            case DETAIL, DPAGE -> "DETAIL";
            case LIST, TAGPAGE, ARCHIVE, FACET, STATIC -> "LIST";
            // 其余取值都没有分页主体（paginatable() == false），契约里没有它们的 page.pageType；
            // 正常路径到不了这里（Pagination 在 kind == null 时留空串），留着是为了让
            // "新增枚举值漏了一行"当场暴露，而不是静默给一个错的页面归属。
            default -> throw new IllegalStateException("无分页主体的页面类型没有 page.pageType：" + this);
        };
    }

    /** 该页面类型是不是 feed 或 404 页（二者共享"没有浏览位置 / 有内置兜底"的语义，§7.3）。 */
    public boolean feedOr404() {
        return this == FEED || this == PAGE404;
    }

    /** 可迭代的具名作用域清单，用于报错文案（§10.3 要求列出可选项）。 */
    public static List<String> names() {
        return List.of("HOME", "LIST", "TAGLIST", "TAGPAGE", "ARCHIVE", "DETAIL", "DPAGE",
                "SINGLE", "FACET", "SEARCH", "STATIC", "404", "feed");
    }
}
