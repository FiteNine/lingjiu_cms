package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * §4.5 第 9 条 ★：{@code url_pattern} 的占位符白名单与必需占位符 → E4001 / E4003。
 *
 * <p><b>期 1 只做到"判定函数"这一层</b>：这里是 §7.1.1 的白名单与 §7.1.3 的判定函数，
 * 全是纯函数（进 pattern 与几个标志，出错抛异常），真正调用它们的页面计划器（{@code PagePlan} /
 * {@code UrlResolver}）是期 2 的活。因此本类**不拼 URL**、不做第 1 页形态的删除
 * （那是 {@code UrlResolver} 的唯一职责，§7.1.2："这条规则由 UrlResolver 唯一实现，模板与文档都不许自己拼"）。
 *
 * <p>判定时机与文案（§7.1.3 的五条）分别落在四个方法上，互不重叠：
 * <ul>
 *   <li>① 编译期：白名单外的占位符 → {@link #checkWhitelist}（E4001，列白名单）；</li>
 *   <li>② 计划期：必需占位符缺失 → {@link #checkRequired}（E4001）；</li>
 *   <li>③ 计划期：有 {@code {n}} 但该页不会分页 → {@link #checkPagination}（E4001）；</li>
 *   <li>④ 计划期：算出 {@code totalPages > 1} 却没有 {@code {n}} → {@link #checkTotalPages}（E4003）；</li>
 *   <li>⑤ 计划期：{@code paginate_body} 非空但 pattern 里没有 {@code {n}} → {@link #checkContentPagination}（E4001）。</li>
 * </ul>
 */
public final class UrlPatternRules {

    /**
     * §7.1.1 的占位符白名单（**唯一权威清单**）。{@code {n}} 与 {@code {pageNo}} 是同一个占位符
     * （{@code {n}} 是简写，不另计个数），两者都列在这里。
     */
    public static final List<String> WHITELIST = List.of(
            "slug", "id", "parentSlug", "sort", "categoryPath", "categorySlug", "typeCode",
            "year", "month", "day", "tagSlug", "facetPath", "pageNo", "n", "lang");

    /** 分页号的两个写法（§7.1.1："同一个占位符"）。 */
    private static final Set<String> PAGE_NO = Set.of("n", "pageNo");

    private UrlPatternRules() {
    }

    /**
     * §7.1.3 判定函数的输入：这一页是哪一种页面。{@code LIST} 拆成两种来源
     * （类型列表页 / 分类索引页，§7.2.1 v2.2 定死），因为两者的必需占位符不同。
     */
    public enum PageKind {
        HOME, TYPE_LIST, CATEGORY_LIST, TAGPAGE, ARCHIVE_YEAR, ARCHIVE_MONTH, DETAIL, SINGLE, FACET
    }

    /* ---------------- ① 白名单与模式串语法 ---------------- */

    /** pattern 里出现的占位符，按出现顺序去重。 */
    public static Set<String> placeholders(String pattern) {
        Set<String> found = new LinkedHashSet<>();
        if (pattern == null) {
            return found;
        }
        int from = 0;
        while (true) {
            int open = pattern.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = pattern.indexOf('}', open + 1);
            if (close < 0) {
                found.add(pattern.substring(open + 1));
                break;
            }
            found.add(pattern.substring(open + 1, close));
            from = close + 1;
        }
        return found;
    }

    /**
     * 白名单与模式串语法（§7.1.1）：白名单外的占位符、不以 {@code /} 开头、以 {@code //} 结尾、
     * 含 {@code ?} 或 {@code #} 一律 E4001。
     *
     * @param pattern 模式串
     * @param where   出处在报错文案里怎么称呼（例如"类型 product 的 detail_url_pattern"）
     */
    public static void checkWhitelist(String pattern, String where) {
        if (pattern == null || pattern.isBlank()) {
            throw error("url_pattern 是空的", where, "（空串）",
                    "模式串必须以 / 开头，例如 /product/{slug}.html");
        }
        // 未闭合的 `{` 是模式串的语法错误：不能把它后面的整段当占位符——那恰好可能是白名单里的
        // 名字（如 /a/{slug），于是"白名单、以 / 开头、不以 // 结尾、无 ? #"全部通过，畸形串静默放行
        int lastOpen = pattern.lastIndexOf('{');
        if (lastOpen >= 0 && pattern.indexOf('}', lastOpen + 1) < 0) {
            throw error("url_pattern 里的 { 没有闭合", where, pattern,
                    "每个 { 都要有对应的 }，例如 /product/{slug}.html");
        }
        for (String placeholder : placeholders(pattern)) {
            if (!WHITELIST.contains(placeholder)) {
                throw error("占位符 {" + placeholder + "} 不在白名单里", where, pattern,
                        "可用占位符只有这些（§7.1.1）：" + String.join(" ", WHITELIST)
                                + Suggest.hint(placeholder, WHITELIST));
            }
        }
        if (!pattern.startsWith("/")) {
            throw error("url_pattern 必须以 / 开头", where, pattern,
                    "静态产物不做相对路径，例如 /news/{slug}.html");
        }
        if (pattern.endsWith("//")) {
            throw error("url_pattern 不能以 // 结尾", where, pattern,
                    "会产出空段（§7.1.1）；目录形态写成 /news/");
        }
        if (pattern.contains("?") || pattern.contains("#")) {
            throw error("url_pattern 不能含 ? 或 #", where, pattern,
                    "静态产物不做查询串（§7.1.1）；排序 / 筛选的每个变体是一个独立静态页");
        }
    }

    /* ---------------- ② 必需占位符（§7.1.3） ---------------- */

    /**
     * 必需占位符的判定函数（§7.1.3 的伪代码逐行落地）。
     *
     * @param kind      页面种类
     * @param paginates 该页是否有分页主体（详情页 = {@code paginate_body} 非空或模板里有 {@code {cms:list}}）
     * @param totalPages 总页数；未知传 0（首页行要用它判断"只有 1 页时不要求 {n}"）
     * @return 每一组"至少有一个"的占位符集合；组之间是 AND，组内是 OR
     */
    public static List<Set<String>> requiredGroups(PageKind kind, boolean paginates, long totalPages) {
        List<Set<String>> groups = new ArrayList<>();
        switch (kind) {
            case HOME -> {
                // 首页分页的 URL 来自站点选项 url.home（默认 /page-{n}/）；只有 1 页时该规则不生效
                if (paginates && totalPages > 1) {
                    groups.add(PAGE_NO);
                }
            }
            case DETAIL -> {
                groups.add(Set.of("slug", "id"));
                if (paginates) {
                    groups.add(PAGE_NO);
                }
            }
            case SINGLE -> {
                // 路径一般写死（如 /about/）；给了 {slug} 也可以
            }
            case TYPE_LIST -> {
                // URL 就是该类型的 list_url_pattern 本身
            }
            case CATEGORY_LIST -> groups.add(Set.of("categoryPath", "categorySlug"));
            case TAGPAGE -> groups.add(Set.of("tagSlug"));
            case ARCHIVE_YEAR -> groups.add(Set.of("year"));
            case ARCHIVE_MONTH -> groups.add(Set.of("year", "month"));
            case FACET -> groups.add(Set.of("facetPath"));
            // 新增 PageKind 时在这里尽早暴露：少了 default 只会静默返回空 groups，
            // 让"必需占位符"校验对该类型完全失效（漏判且无任何提示）
            default -> throw new IllegalArgumentException("未处理的 PageKind: " + kind);
        }
        return groups;
    }

    /** 必需占位符缺失 → E4001（指明缺哪个、该页面类型要什么）。 */
    public static void checkRequired(String pattern, List<Set<String>> groups, String where) {
        Set<String> found = placeholders(pattern);
        for (Set<String> group : groups) {
            if (group.stream().anyMatch(found::contains)) {
                continue;
            }
            String need = group.size() == 1
                    ? "{" + group.iterator().next() + "}"
                    : "{" + String.join("} 或 {", new java.util.TreeSet<>(group)) + "}";
            throw error("缺少必需占位符 " + need, where, pattern,
                    "该页面类型的必需占位符见 §7.1.3；当前 pattern 里有 "
                            + (found.isEmpty() ? "（没有占位符）" : "{" + String.join("} {", found) + "}"));
        }
    }

    /* ---------------- ③④⑤ 与分页相关的三条 ---------------- */

    /** ③ 计划期：pattern 里有 {@code {n}} 但该页不会分页 → E4001。 */
    public static void checkPagination(String pattern, boolean paginates, String where) {
        if (!paginates && hasPageNo(pattern)) {
            throw error("占位符 {n} 用在不分页的页面上", where, pattern,
                    "该页没有分页主体（既无 paginate_body 又无 {cms:list}）；"
                            + "去掉 {n}，或给页面加一个分页主体（§7.1.3 第 3 条）");
        }
    }

    /** ④ 计划期：会分页（{@code totalPages > 1}）却没有 {@code {n}} → E4003。 */
    public static void checkTotalPages(String pattern, long totalPages, String where) {
        if (totalPages > 1 && !hasPageNo(pattern)) {
            throw PublishException.error(PublishErrorCode.E4003,
                    "共 " + totalPages + " 页，但 url_pattern 没有 {n}", null, 0,
                    where + "：" + pattern,
                    "改成 /news/page-{n}/ 这样的形态，或把每页条数调大到总页数不超过 1 页"
                            + "（每页条数由类型 / 列表页声明，这里算不出具体值）");
        }
    }

    /** ⑤ 计划期：{@code paginate_body} 非空但 pattern 里没有 {@code {n}} → E4001。 */
    public static void checkContentPagination(String pattern, boolean contentPagination, String where) {
        if (contentPagination && !hasPageNo(pattern)) {
            throw error("paginate_body 非空，但 url_pattern 里没有 {n}", where, pattern,
                    "正文分页要产出第 2..N 页，URL 必须能表达页号；"
                            + "否则 {cms:detail} 之后的内容会被静默丢掉（§7.1.3 第 5 条）");
        }
    }

    /** pattern 里有没有分页号（{@code {n}} 与 {@code {pageNo}} 等价）。 */
    public static boolean hasPageNo(String pattern) {
        return placeholders(pattern).stream().anyMatch(PAGE_NO::contains);
    }

    private static PublishException error(String what, String where, String actual, String advice) {
        return PublishException.error(PublishErrorCode.E4001, what, null, 0,
                where + "：" + actual, advice);
    }
}
