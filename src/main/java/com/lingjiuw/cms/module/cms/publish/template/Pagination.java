package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.model.PageType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分页模型（static-publish.md §5.6）：{@code page} 作用域的填装与分页切片。
 *
 * <p>**它只管"这一页是第几页、一共几页、能不能翻"**：查一页数据是标签的事（{@code {cms:list}}
 * 按 {@link #offset()} / {@link #row()} 查），填作用域是这里的事。这样"派生页只覆盖
 * {@code page.pageNo} 重渲染"（§5.4 第 5 步）就落在一条 {@code new Pagination(pageNo, ...)} 上，
 * 标签里不需要任何"第几页"的状态。
 *
 * <p>§5.6 的字段分成两组，一起填：
 * <ul>
 *   <li>17 个分页字段（{@code pageNo} … {@code lastUrl}）——**只在有分页主体时有值**；</li>
 *   <li>6 个"所有页面都有"的字段（{@code url} {@code title} {@code canonical} {@code noindex}
 *       {@code robots} {@code lastmod}）——不是分页字段，但同放 {@code page} 作用域。
 *       它们由页面计划填；本类只在**还没值**时补上"这一页的 URL"，
 *       因为 {@code page.url} 必须跟着页号走（第 2 页写第 1 页的 canonical 是收录事故，§5.6）。</li>
 * </ul>
 *
 * <p>{@code page.paginationKind} 由调用方给：{@code list}（{@code {cms:list}}）/
 * {@code content}（正文分页）。{@code {cms:pagelist}} 只许在 {@code list} 上用（E3003）。
 */
public record Pagination(int pageNo, int totalPages, long totalCount, int pageSize,
                         PaginationKind kind) {

    /** "没有分页主体"的取值：{@code totalPages=0}、{@code paginationKind} 为空串（§5.6）。 */
    public static final Pagination NO_BODY = new Pagination(1, 0, 0, 0, null);

    public Pagination {
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (totalPages < 0) {
            totalPages = 0;
        }
        if (pageSize < 0) {
            pageSize = 0;
        }
    }

    /**
     * 按总条数算分页：{@code totalPages = ceil(totalCount / pageSize)}，**空集也有第 1 页**
     * （§5.6："列表页允许没有 {cms:list}，则该页只有第 1 页"——一个存在的空页，
     * 与"根本没有分页主体"是两回事，后者用 {@link #NO_BODY}）。
     *
     * @param pageNo     当前页号，从 1 起
     * @param totalCount 满足条件的总条数（必须与列表查询同一个判定函数，§6.3）
     * @param pageSize   每页条数；{@code <=0} 表示不分页（只有第 1 页）
     */
    public static Pagination of(int pageNo, long totalCount, int pageSize, PaginationKind kind) {
        // 页数先用 long 算再收敛到 int：超过 int 上限时强转会截断甚至变负，
        // 随后被紧凑构造器归零，把"有分页主体"的页面退化成 NO_BODY
        long pages = pageSize <= 0 ? 1L : Math.max(1L, (totalCount + pageSize - 1) / pageSize);
        int totalPages = (int) Math.min(pages, Integer.MAX_VALUE);
        return new Pagination(pageNo, totalPages, totalCount, pageSize, kind);
    }

    /** 当前页在结果集里的偏移量；查这一页的数据时用它。 */
    public int offset() {
        if (pageSize <= 0) {
            return 0;
        }
        // 先按 long 算：(pageNo - 1) * pageSize 是 int×int，乘积超过 int 上限会溢出成负数
        long offset = (long) (pageNo - 1) * pageSize;
        return offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }

    /** 这一页有多少条（最后一页可能不满；没有内容时 0）。 */
    public int row() {
        if (pageSize <= 0) {
            return 0;
        }
        long remaining = totalCount - (long) offset();
        return (int) Math.max(0, Math.min(pageSize, remaining));
    }

    public boolean isFirst() {
        return pageNo <= 1;
    }

    public boolean isLast() {
        return pageNo >= totalPages;
    }

    /** 这一页没有任何条目。 */
    public boolean empty() {
        return totalCount <= 0;
    }

    /** 有结果（{@code empty} 的反面，§6.3 要求两个都给）。 */
    public boolean hasResults() {
        return totalCount > 0;
    }

    public boolean hasPrev() {
        return pageNo > 1;
    }

    public boolean hasNext() {
        return pageNo < totalPages;
    }

    /* ---------------- 填作用域 ---------------- */

    /**
     * 把 §5.6 的分页字段填进 {@code page} 作用域。
     *
     * @param ctx         渲染上下文
     * @param pageType    当前页面类型；{@code page.pageType} 取它的
     *                    {@link PageType#pageTypeCode()}（{@code DPAGE} 因此仍是 {@code DETAIL}）
     * @param currentUrl  这一页自己的 URL；没注入 {@link PageUrlBuilder} 时传 null
     */
    public void fill(RenderContext ctx, PageType pageType, String currentUrl) {
        PageUrlBuilder urls = ctx.pageUrls();
        String siteUrl = ctx.namedValues("site").get("url") == null
                ? "" : String.valueOf(ctx.namedValues("site").get("url"));
        Map<String, Object> scope = fillInto(pageType, currentUrl, urls, siteUrl);
        // "所有页面都有"的 6 个里有 4 个是主题/计划的事（title / noindex / robots / lastmod），
        // 已经填过的不覆盖；paginationKind 由分页主体决定，也必须由分页主体覆盖
        for (String key : List.of("title", "noindex", "robots", "lastmod", "url", "canonical")) {
            if (ctx.pageVar(key) != null) {
                scope.remove(key);
            }
        }
        ctx.putPageAll(scope);
    }

    /** 算出要写进 {@code page} 的全部字段；不碰上下文，好让 {@link #toScope} 与测试直接读。 */
    private Map<String, Object> fillInto(PageType pageType, String currentUrl, PageUrlBuilder urls,
                                         String siteUrl) {
        String first = urls == null ? "" : nullToEmpty(urls.firstPageUrl());
        String current = currentUrl == null ? "" : currentUrl;
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("pageNo", pageNo);
        scope.put("totalPages", totalPages);
        scope.put("totalCount", totalCount);
        scope.put("pageSize", pageSize);
        // 没有分页主体时这两个留空串：模板据此判"这一页根本没有分页"（§5.6）
        scope.put("pageType", pageType == null || kind == null ? "" : pageType.pageTypeCode());
        scope.put("paginationKind", kind == null ? "" : kind.code());
        scope.put("isFirst", isFirst());
        scope.put("isLast", isLast());
        scope.put("empty", empty());
        scope.put("hasResults", hasResults());
        scope.put("hasPrev", hasPrev());
        scope.put("hasNext", hasNext());
        scope.put("currentUrl", current);
        scope.put("firstUrl", first);
        scope.put("prevUrl", urls != null && hasPrev() ? nullToEmpty(urls.pageUrl(pageNo - 1)) : "");
        scope.put("nextUrl", urls != null && hasNext() ? nullToEmpty(urls.pageUrl(pageNo + 1)) : "");
        scope.put("lastUrl", urls != null && totalPages > 0 ? nullToEmpty(urls.pageUrl(totalPages)) : "");
        // "所有页面都有"的那 6 个字段（不是分页字段，但同放 page 作用域，§5.6）
        scope.put("url", current);
        scope.put("canonical", current.isEmpty() ? "" : siteUrl + current);
        scope.put("title", "");
        scope.put("noindex", Boolean.FALSE);
        scope.put("robots", "");
        scope.put("lastmod", "");
        return scope;
    }

    /**
     * 没有分页主体的页面（{@code {cms:query}} 独撑的首页、{@code SINGLE}、{@code feed}…）
     * 也要有 {@code page} 作用域：§5.6 的"所有页面都有"那 6 个字段，
     * 加上"没有分页"的取值（{@code totalPages=0}、{@code paginationKind=''}）。
     * {@code {cms:pagelist}} 据此不渲染任何页号。
     */
    public static void fillWithoutBody(RenderContext ctx, String currentUrl) {
        NO_BODY.fill(ctx, ctx.pageType(), currentUrl);
    }

    /** {@code page} 作用域的字段快照（只读，不改动上下文）；测试与页面计划直接读它。 */
    public Map<String, Object> toScope(PageType pageType, String currentUrl, PageUrlBuilder urls,
                                       String siteUrl) {
        return fillInto(pageType, currentUrl, urls, siteUrl == null ? "" : siteUrl);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
