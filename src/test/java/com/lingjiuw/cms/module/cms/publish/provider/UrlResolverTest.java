package com.lingjiuw.cms.module.cms.publish.provider;

import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.UrlPatternResolver;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7.1 的 URL 规则：数据层算 {@code url} / 分类索引 URL / 标签页 URL / 归档页 URL 时**唯一**依赖它。
 *
 * <p><b>这份实现不在本包</b>：共享的 {@link UrlPatternResolver}（{@code publish/model}）是唯一实现
 * ——"列表里的链接"与"产物路径"必须同源，因此这里断言的是**数据层对它的使用契约**，
 * 包括本包唯一自己决定的那件事：{@code detail_url_pattern} 为空时兜底
 * {@code /{typeCode}/{slug}.html}（{@link DbContentProvider#detailPattern}）。
 */
class UrlResolverTest {

    /* ---------------- §7.1.1 / §7.1.2：第 1 页形态的四个官方例子，逐字节 ---------------- */

    @Test
    void 第1页删除占位符与一个相邻分隔符_四个官方例子逐字节成立() {
        assertEquals("/news/", resolve("/news/page-{n}/", Map.of("typeCode", "article"), 1));
        assertEquals("/book/abc/list.html", resolve("/book/{slug}/list-{n}.html", Map.of("slug", "abc"), 1));
        assertEquals("/news/x.html", resolve("/{categoryPath}/{slug}-{n}.html",
                Map.of("categoryPath", "news", "slug", "x"), 1));
        assertEquals("/news/", resolve("/{categoryPath}/page-{n}/", Map.of("categoryPath", "news"), 1));
    }

    @Test
    void 第2页起把页号换进同一个占位符() {
        assertEquals("/news/page-2/", resolve("/news/page-{n}/", Map.of(), 2));
        assertEquals("/news/page-12/", resolve("/news/page-{n}/", Map.of(), 12));
        // {n} 与 {pageNo} 是同一个占位符（§7.1.1）
        assertEquals("/news/page-3/", resolve("/news/page-{pageNo}/", Map.of(), 3));
        assertEquals("/news/", resolve("/news/page-{pageNo}/", Map.of(), 1));
    }

    @Test
    void pageUrls给分页主体两个形态() {
        PageUrlBuilder urls = UrlPatternResolver.pageUrls("/news/page-{n}/", Map.of());
        assertEquals("/news/", urls.firstPageUrl());
        assertEquals("/news/page-2/", urls.pageUrl(2));
        assertEquals("/news/page-5/", urls.pageUrl(5));

        // 不分页的模式串：任何页号都是同一个 URL
        PageUrlBuilder single = UrlPatternResolver.pageUrls("/about/", Map.of());
        assertEquals("/about/", single.firstPageUrl());
        assertEquals("/about/", single.pageUrl(3));
        assertTrue(!UrlPatternResolver.paginates("/about/"));
    }

    /* ---------------- §7.1.2：小写与占位符取值 ---------------- */

    @Test
    void URL一律小写且只接受白名单占位符() {
        // 占位符的取值一律小写（§7.1.2：URL 里只允许 ASCII 且一律小写，Linux 文件系统大小写敏感）
        assertEquals("/news/abc.html", UrlPatternResolver.resolve("/{typeCode}/{slug}.html",
                Map.of("typeCode", "NEWS", "slug", "ABC")));
        // 白名单外的占位符（这里连大小写写错也算表外）一律报错，不静默留下 {foo}
        assertThrows(UrlPatternResolver.IllegalPatternException.class,
                () -> UrlPatternResolver.resolve("/product/{brand}.html", Map.of("brand", "acme")));
        assertThrows(UrlPatternResolver.IllegalPatternException.class,
                () -> UrlPatternResolver.resolve("/{TyPeCode}/{slug}.html", Map.of()));
    }

    @Test
    void 归档的月日补零() {
        // {month} / {day} 一律补零：/archive/2026/03/ 与 /archive/2026/3/ 是两个产物路径
        assertEquals("/archive/2026/03/", UrlPatternResolver.resolve("/archive/{year}/{month}/",
                Map.of("year", 2026, "month", 3)));
        // 年模式没有月：占位符与紧邻的一个分隔符一起删（§7.1.1）
        assertEquals("/archive/2026/", UrlPatternResolver.resolve("/archive/{year}/{month}/",
                Map.of("year", 2026)));
        assertEquals("/tag/hot/", UrlPatternResolver.resolve("/tag/{tagSlug}/", Map.of("tagSlug", "hot")));
        assertEquals("/f/brand-acme/", UrlPatternResolver.resolve("/f/{facetPath}/",
                Map.of("facetPath", "brand-acme")));
    }

    @Test
    void 类型的列表页与站点语言占位符() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("typeCode", "product");
        values.put("lang", "zh-CN");
        assertEquals("/product/page-3/", UrlPatternResolver.resolve("/{typeCode}/page-{n}/", values, 3));
        assertEquals("/product/", UrlPatternResolver.resolve("/{typeCode}/page-{n}/", values, 1));
        // {lang} 也会被规范成小写（URL 一律小写）；hreflang 用的是 site.lang 原值，不受影响
        assertEquals("/zh-cn/index/", UrlPatternResolver.resolve("/{lang}/index/", values));
    }

    /* ---------------- 数据层自己的那一条：pattern 为空时的兜底 ---------------- */

    @Test
    void detail_url_pattern为空时兜底到类型与slug() {
        // §7.1.1：模式串为空是"没配"，引擎不能因此产出空链接
        ContentTypeDef blank = new ContentTypeDef(1, "article", "文章", ContentTypeDef.Kind.CONTENT,
                false, null, null, null, null, null, "publishTime", "desc", 20, null, null,
                java.util.List.of(), java.util.Map.of());
        String pattern = DbContentProvider.detailPattern(blank);
        assertEquals("/{typeCode}/{slug}.html", pattern);
        assertEquals("/article/a.html", UrlPatternResolver.resolve(pattern,
                Map.of("typeCode", "article", "slug", "a")));

        ContentTypeDef configured = new ContentTypeDef(2, "book", "书", ContentTypeDef.Kind.TREE,
                true, "/book/{parentSlug}/{slug}-{sort}.html", null, null, null, null, "sort", "asc",
                20, null, null, java.util.List.of(), java.util.Map.of());
        assertEquals("/book/{parentSlug}/{slug}-{sort}.html", DbContentProvider.detailPattern(configured));
    }

    @Test
    void 层级类型带父slug与序号的详情URL() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("slug", "ch-1");
        values.put("parentSlug", "book-a");
        values.put("sort", 3);
        values.put("typeCode", "chapter");
        assertEquals("/book/book-a/ch-1-3.html",
                UrlPatternResolver.resolve("/book/{parentSlug}/{slug}-{sort}.html", values));
        // 顶层内容没有父 slug：占位符与相邻的一个分隔符一起删
        values.remove("parentSlug");
        assertEquals("/book/ch-1-3.html",
                UrlPatternResolver.resolve("/book/{parentSlug}/{slug}-{sort}.html", values));
    }

    private static String resolve(String pattern, Map<String, Object> values, Integer pageNo) {
        return UrlPatternResolver.resolve(pattern, values, pageNo);
    }
}
