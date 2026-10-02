package com.lingjiuw.cms.module.cms.publish.model;

import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * URL 规则的唯一实现（static-publish.md §7.1.1、§7.1.2、§7.1.3）。
 *
 * <p>数据层（算一条内容 / 一个分类的 {@code url}）与页面计划（算一个页面的 URL 与分页形态）
 * 必须**用同一份规则**，否则"列表里的链接"与"真实产物路径"会悄悄分叉——那是静态站最难查的
 * 一类 bug（点得开 404，或者点开的是另一个页面）。因此占位符替换、越界校验与"第 1 页不含
 * {@code {n}}"这三件事都只写在这里。
 *
 * <p>两条承重规则：
 * <ul>
 *   <li><b>白名单外的占位符一律报错</b>（{@link IllegalPatternException}），并把白名单列出来——
 *       静默留下 {@code {foo}} 会在产物里生成一个没人能访问的路径；</li>
 *   <li><b>可选占位符缺值 = 连同紧邻的一个分隔符一起逐字符删除</b>，然后清理"被删空的路径段"。
 *       契约特意强调"不是截断到某个位置"（§7.1.1 v2.2），因为两种实现会给出 {@code list.html}
 *       与 {@code list-1.html} 两个不同结果（后者还会造成重复收录）。</li>
 * </ul>
 *
 * <p><b>第 1 页形态的完整规则</b>（由 §7.1.1 的四个例子反推并逐条验证，见
 * {@code UrlPatternResolverTest}）：
 * <ol>
 *   <li>删掉 {@code {n}} 自身；</li>
 *   <li>若它右侧紧邻分隔符（{@code /} 或 {@code -}）→ 一起删；否则左侧紧邻的分隔符一起删；</li>
 *   <li>清理**被删空的路径段**：剩下的那一段若正好紧挨着被删掉的 {@code {n}}（即"页前缀"那一段，
 *       如 {@code page-} / {@code list-}），整段连同它的分隔符一起删；文件形态（含 {@code .}）只删分隔符。</li>
 * </ol>
 * 四个官方例子因此成立：{@code /news/page-{n}/} → {@code /news/}；
 * {@code /book/{slug}/list-{n}.html} → {@code /book/{slug}/list.html}；
 * {@code /{categoryPath}/{slug}-{n}.html} → {@code /{categoryPath}/{slug}.html}；
 * {@code /{categoryPath}/page-{n}/} → {@code /{categoryPath}/}。
 */
public final class UrlPatternResolver {

    /** 占位符白名单（§7.1.1 的"唯一权威清单"，与 {@code UrlPatternRules.WHITELIST} 同一份取值）。 */
    public static final List<String> PLACEHOLDERS = List.of(
            "slug", "id", "parentSlug", "sort", "categoryPath", "categorySlug", "typeCode",
            "year", "month", "day", "tagSlug", "facetPath", "pageNo", "n", "lang");

    /** 分页占位符的两个写法（{@code {n}} 是 {@code {pageNo}} 的简写，不另计个数）。 */
    public static final List<String> PAGE_PLACEHOLDERS = List.of("n", "pageNo");

    /** 可选占位符缺值时，优先删掉的那个分隔符（§7.1.1："/ 或 -"）。 */
    private static final String SEPARATORS = "/-";

    private UrlPatternResolver() {
    }

    /** 用取值表替换 pattern 里的占位符；缺值的占位符按"第 1 页形态"的规则删除。 */
    public static String resolve(String pattern, Map<String, Object> values) {
        return resolve(pattern, values, null);
    }

    /**
     * @param pageNo 页码；{@code null} = 不涉及分页（{@code {n}} 若给了值就用它）；
     *               {@code <=1} = 第 1 页，走删除规则；{@code >1} = 把 {@code {n}} 换成它
     */
    public static String resolve(String pattern, Map<String, Object> values, Integer pageNo) {
        Map<String, Object> all = new LinkedHashMap<>(values == null ? Map.of() : values);
        if (pageNo != null) {
            if (pageNo > 1) {
                all.put("n", pageNo);
                all.put("pageNo", pageNo);
            } else {
                // 第 1 页：显式置空，走"可选占位符缺值"的删除规则
                all.put("n", null);
                all.put("pageNo", null);
            }
        }
        return substitute(pattern, all, true);
    }

    /**
     * @param strict {@code true} = 白名单外的占位符报错；{@code false} = 原样保留
     *               （只有诊断与测试用，产物路径一律 {@code true}）
     */
    private static String substitute(String pattern, Map<String, Object> values, boolean strict) {
        String text = pattern == null ? "" : pattern.trim();
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        // 含分页占位符的那一段的起点与**终点**（在 out 里的下标）；-1 = 这一段不构成"纯页前缀段"。
        // 终点必须单独记：拿 out.length() 当段尾会把 {n} 之后的段一起吃掉
        // （/{typeCode}/page-{n}/{id} 的第 1 页是 /product/42，不是 /product/）。
        int pageSegmentStart = -1;
        int pageSegmentEnd = -1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '/') {
                pageSegmentStart = -1;
                out.append(c);
                i++;
                continue;
            }
            if (c != '{') {
                // 占位符之后还有字面量 → 段没有结束，"整段删"作废
                // （/book/{slug}/list-{n}.html 的 list- 后面还有 .html，要保留文件名）
                pageSegmentStart = -1;
                out.append(c);
                i++;
                continue;
            }
            int close = text.indexOf('}', i + 1);
            if (close < 0) {
                throw new IllegalPatternException("占位符没有闭合的 '}'：" + pattern);
            }
            String name = text.substring(i + 1, close).trim();
            if (!PLACEHOLDERS.contains(name)) {
                if (strict) {
                    throw new IllegalPatternException("占位符 {" + name + "} 不在白名单里：" + pattern
                            + "；可用的是 " + String.join(" ", PLACEHOLDERS));
                }
                out.append(text, i, close + 1);
                i = close + 1;
                continue;
            }
            String value = stringOf(values, name);
            if (!value.isEmpty()) {
                out.append(value);
                i = close + 1;
                continue;
            }
            // ---- 缺值：可选占位符删除 ----
            if (PAGE_PLACEHOLDERS.contains(name)) {
                pageSegmentStart = segmentStartIn(out);
                pageSegmentEnd = out.length();
            }
            if (close + 1 < text.length() && SEPARATORS.indexOf(text.charAt(close + 1)) >= 0) {
                // 右侧紧邻的分隔符被一起删掉 ⇒ 这一段到此结束：**立刻**整段删掉再继续。
                // 留到最后再删就会把后面新追加的内容（{id} 之类的取值）也算进这一段的段尾。
                if (pageSegmentStart >= 0) {
                    out.delete(pageSegmentStart, out.length());
                }
                pageSegmentStart = -1;
                pageSegmentEnd = -1;
                i = close + 2;
            } else {
                int length = out.length();
                if (length > 0 && SEPARATORS.indexOf(out.charAt(length - 1)) >= 0) {
                    out.deleteCharAt(length - 1);
                }
                pageSegmentEnd = out.length();
                i = close + 1;
            }
        }
        // 页号被删空且这一段确实是页前缀（page- / list-）→ 只删这一段，不动后面的段。
        // 段里还有别的字面量时 pageSegmentStart 已经在上面被清成 -1，文件名不会被误删。
        if (pageSegmentStart >= 0 && pageSegmentEnd > pageSegmentStart && pageSegmentEnd <= out.length()) {
            out.delete(pageSegmentStart, pageSegmentEnd);
        }
        return normalize(out.toString());
    }

    /** {@code out} 里最后一段的起点（最后一个 '/' 之后）。 */
    private static int segmentStartIn(StringBuilder out) {
        for (int i = out.length() - 1; i >= 0; i--) {
            if (out.charAt(i) == '/') {
                return i + 1;
            }
        }
        return 0;
    }

    private static String stringOf(Map<String, Object> values, String name) {
        Object raw = values.get(name);
        if (raw == null && PAGE_PLACEHOLDERS.contains(name)) {
            for (String alias : PAGE_PLACEHOLDERS) {
                if (values.get(alias) != null) {
                    raw = values.get(alias);
                    break;
                }
            }
        }
        String value = raw == null ? "" : String.valueOf(raw).trim();
        // {month} / {day} 一律补零（/archive/2026/03/ 与 /archive/2026/3/ 是两个不同的产物路径，
        // 而归档页是按同一个 date_trunc 分组的——补零只有一处实现才不会分叉）
        if (("month".equals(name) || "day".equals(name)) && value.matches("\\d")) {
            value = "0" + value;
        }
        return value;
    }

    /**
     * URL 规范化（§7.1.2）：一律小写、折叠重复的 {@code /}、去多余的斜杠
     * （但**保留一个**表示"目录页"的尾斜杠——{@code /about/} 与 {@code /about} 是两个不同的产物文件）。
     *
     * <p><b>只清理分隔符，不清理取值</b>：连字符是 slug 的合法字符（§7.1.2 的白名单是
     * {@code [a-z0-9]} 与 {@code -}），所以 {@code --}、首尾的 {@code -} 一律原样保留——
     * 把 {@code acme--1000} 折成 {@code acme-1000} 会让两个本应不同的 URL 落到同一个产物文件上。
     */
    public static String normalize(String url) {
        String text = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return "";
        }
        int scheme = text.indexOf("://");
        String prefix = scheme < 0 ? "" : text.substring(0, scheme + 3);
        String rest = scheme < 0 ? text : text.substring(scheme + 3);
        rest = rest.replaceAll("/{2,}", "/");
        text = prefix + rest;
        boolean directory = text.endsWith("/");
        text = text.replaceAll("^/+", "/");
        while (text.length() > 1 && text.endsWith("//")) {
            text = text.substring(0, text.length() - 1);
        }
        if (directory && text.length() > 1 && !text.endsWith("/")) {
            text = text + "/";
        }
        return text;
    }

    /**
     * 模式串的静态校验（编译期与计划期都调）：必须以 {@code /} 开头、不得以 {@code //} 结尾、
     * 不得含 {@code ?} / {@code #}，且占位符都在白名单内。
     */
    public static void validate(String pattern) {
        String text = pattern == null ? "" : pattern.trim();
        if (text.isEmpty()) {
            return;
        }
        if (!text.startsWith("/")) {
            throw new IllegalPatternException("URL 规则必须以 / 开头：" + pattern);
        }
        if (text.endsWith("//")) {
            throw new IllegalPatternException("URL 规则不能以 // 结尾（会产出空段）：" + pattern);
        }
        if (text.indexOf('?') >= 0 || text.indexOf('#') >= 0) {
            throw new IllegalPatternException("URL 规则不能含 ? 或 #（静态产物不做查询串）：" + pattern);
        }
        substitute(text, Map.of(), true);
    }

    /**
     * 一个模式的**分页形态**（§7.1.3）：第 1 页不含 {@code {n}}，第 2..N 页把 {@code {n}} 换成页号。
     * 模式里没有 {@code {n}} 时任意页都返回同一个 URL（{@link PageUrlBuilder#single}）。
     */
    public static PageUrlBuilder pageUrls(String pattern, Map<String, Object> values) {
        Map<String, Object> all = new LinkedHashMap<>(values == null ? Map.of() : values);
        String first = resolve(pattern, all, 1);
        String second = resolve(pattern, all, 2);
        if (first.equals(second)) {
            return PageUrlBuilder.single(first);
        }
        return new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return first;
            }

            @Override
            public String pageUrl(int pageNo) {
                return resolve(pattern, all, pageNo);
            }
        };
    }

    /** URL → 产物相对路径（§7.1.2）：{@code /} → {@code index.html}，尾斜杠 → {@code …/index.html}，其余原样。 */
    public static String toArtifactPath(String url) {
        String path = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        if (path.isEmpty() || "/".equals(path)) {
            return "index.html";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            return path + "index.html";
        }
        return path;
    }

    /** 模式里是否含分页占位符（{@code {n}} 或 {@code {pageNo}}）。 */
    public static boolean paginates(String pattern) {
        return pattern != null && (pattern.contains("{n}") || pattern.contains("{pageNo}"));
    }

    /** 模式里的占位符清单，按出现顺序去重（诊断与报错文案用）。 */
    public static List<String> placeholdersOf(String pattern) {
        List<String> found = new ArrayList<>();
        if (pattern == null) {
            return found;
        }
        int i = 0;
        while ((i = pattern.indexOf('{', i)) >= 0) {
            int close = pattern.indexOf('}', i + 1);
            if (close < 0) {
                break;
            }
            String name = pattern.substring(i + 1, close).trim();
            if (!found.contains(name)) {
                found.add(name);
            }
            i = close + 1;
        }
        return List.copyOf(found);
    }

    /** 模式串不合法（白名单外占位符 / 形态违规）。调用方把它转成 E4001。 */
    public static class IllegalPatternException extends RuntimeException {
        public IllegalPatternException(String message) {
            super(message);
        }
    }
}
