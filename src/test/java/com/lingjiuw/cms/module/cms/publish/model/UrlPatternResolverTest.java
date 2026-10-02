package com.lingjiuw.cms.module.cms.publish.model;

import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UrlPatternResolver} 的判据（static-publish.md §7.1.1、§7.1.2）。
 *
 * <p>§7.1.1 的第 1 页形态特意强调"是逐字符删除 {@code {n}} 与其紧邻的一个分隔符，不是截断到
 * 某个位置"——这份用例把契约给的四个例子逐条钉住，因为两种实现会产出
 * {@code list.html} 与 {@code list-1.html} 两个不同结果（后者会造成重复收录）。
 */
@DisplayName("URL 规则：占位符替换与第 1 页形态（§7.1.1）")
class UrlPatternResolverTest {

    @Test
    @DisplayName("契约的四个第 1 页例子逐字节成立")
    void contractExamples() {
        // 1. /news/page-{n}/ 的第 1 页是 /news/
        assertThat(UrlPatternResolver.resolve("/news/page-{n}/", Map.of(), 1)).isEqualTo("/news/");
        assertThat(UrlPatternResolver.resolve("/news/page-{n}/", Map.of(), 2)).isEqualTo("/news/page-2/");

        // 2. /book/{slug}/list-{n}.html 的第 1 页是 /book/{slug}/list.html
        assertThat(UrlPatternResolver.resolve("/book/{slug}/list-{n}.html", Map.of("slug", "x"), 1))
                .isEqualTo("/book/x/list.html");
        assertThat(UrlPatternResolver.resolve("/book/{slug}/list-{n}.html", Map.of("slug", "x"), 3))
                .isEqualTo("/book/x/list-3.html");

        // 3. /{categoryPath}/{slug}-{n}.html 的第 1 页是 /{categoryPath}/{slug}.html
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/{slug}-{n}.html",
                Map.of("categoryPath", "news/tech", "slug", "post"), 1))
                .isEqualTo("/news/tech/post.html");
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/{slug}-{n}.html",
                Map.of("categoryPath", "news/tech", "slug", "post"), 4))
                .isEqualTo("/news/tech/post-4.html");

        // 4. /{categoryPath}/page-{n}/ 的第 1 页是 /{categoryPath}/
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/page-{n}/", Map.of("categoryPath", "case"), 1))
                .isEqualTo("/case/");
    }

    @Test
    @DisplayName("{n} 与 {pageNo} 是同一个占位符")
    void pageNoAliases() {
        assertThat(UrlPatternResolver.resolve("/news/page-{pageNo}/", Map.of(), 1)).isEqualTo("/news/");
        assertThat(UrlPatternResolver.resolve("/news/page-{pageNo}/", Map.of(), 5)).isEqualTo("/news/page-5/");
        assertThat(UrlPatternResolver.resolve("/news/page-{n}/", Map.of("pageNo", 7)))
                .isEqualTo("/news/page-7/");
    }

    @Test
    @DisplayName("路径形态与文件形态的分工：段被删空时文件形态只丢分隔符")
    void pathVersusFileShape() {
        // 目录形态：page- 只是页前缀，整段删
        assertThat(UrlPatternResolver.resolve("/product/page-{n}/", Map.of(), 1)).isEqualTo("/product/");
        assertThat(UrlPatternResolver.resolve("/rank/page-{n}/", Map.of(), 1)).isEqualTo("/rank/");
        // 文件形态：文件名要保留下来（list-.html → list.html，而不是丢掉整段变成 /book/x/）
        assertThat(UrlPatternResolver.resolve("/book/{slug}/list-{n}.html", Map.of("slug", "a"), 1))
                .isEqualTo("/book/a/list.html");
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/page-{n}.html", Map.of("categoryPath", "n"), 1))
                .isEqualTo("/n/page.html");
    }

    @Test
    @DisplayName("没有 {n} 的模式原样输出（单页 / 详情页）")
    void noPagination() {
        assertThat(UrlPatternResolver.resolve("/about/", Map.of(), 1)).isEqualTo("/about/");
        assertThat(UrlPatternResolver.resolve("/about/", Map.of(), 9)).isEqualTo("/about/");
        assertThat(UrlPatternResolver.resolve("/product/{slug}.html", Map.of("slug", "acme-1000"), 1))
                .isEqualTo("/product/acme-1000.html");
        assertThat(UrlPatternResolver.resolve("/tag/{tagSlug}/", Map.of("tagSlug", "java"), 1))
                .isEqualTo("/tag/java/");
    }

    @Test
    @DisplayName("可选占位符缺值：连同紧邻的一个分隔符一起删除")
    void optionalPlaceholderMissing() {
        // {categorySlug} 缺值 → 连左侧的 '-' 一起删
        assertThat(UrlPatternResolver.resolve("/news/{categorySlug}-{slug}.html", Map.of("slug", "p"), 1))
                .isEqualTo("/news/p.html");
        // {sort} 缺值 → 连左侧的 '-' 一起删
        assertThat(UrlPatternResolver.resolve("/book/{slug}/chapter-{sort}-{slug}.html",
                Map.of("slug", "c1"), 1))
                .isEqualTo("/book/c1/chapter-c1.html");
        // 缺值占位符在段首 → 连右侧的 '/' 一起删
        assertThat(UrlPatternResolver.resolve("/{lang}/news/{slug}.html", Map.of("slug", "p"), 1))
                .isEqualTo("/news/p.html");
    }

    @Test
    @DisplayName("URL 规范化：小写、折叠重复分隔符、保留目录形态的尾斜杠")
    void normalization() {
        assertThat(UrlPatternResolver.resolve("/NEWS/{slug}.html", Map.of("slug", "Post"), 1))
                .isEqualTo("/news/post.html");
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/{slug}.html",
                Map.of("categoryPath", "news/tech", "slug", "p"), 1))
                .isEqualTo("/news/tech/p.html");
        assertThat(UrlPatternResolver.resolve("/f/{facetPath}/", Map.of("facetPath", "brand-acme"), 1))
                .isEqualTo("/f/brand-acme/");
    }

    @Test
    @DisplayName("白名单外的占位符报错，并把白名单列出来（E4001 的口径）")
    void whitelistEnforced() {
        assertThatThrownBy(() -> UrlPatternResolver.resolve("/news/{foo}/", Map.of(), 1))
                .isInstanceOf(UrlPatternResolver.IllegalPatternException.class)
                .hasMessageContaining("{foo}")
                .hasMessageContaining("slug");

        assertThatThrownBy(() -> UrlPatternResolver.validate("news/{slug}"))
                .isInstanceOf(UrlPatternResolver.IllegalPatternException.class)
                .hasMessageContaining("必须以 / 开头");
        assertThatThrownBy(() -> UrlPatternResolver.validate("/news//"))
                .hasMessageContaining("不能以 // 结尾");
        assertThatThrownBy(() -> UrlPatternResolver.validate("/news/{slug}.html?a=1"))
                .hasMessageContaining("不能含 ? 或 #");
    }

    @Test
    @DisplayName("PageUrlBuilder：第 1 页不含 {n}，第 2..N 页含；不分页的模式退化为单页")
    void pageUrlBuilder() {
        PageUrlBuilder paged = UrlPatternResolver.pageUrls("/news/page-{n}/", Map.of());
        assertThat(paged.firstPageUrl()).isEqualTo("/news/");
        assertThat(paged.pageUrl(1)).isEqualTo("/news/");
        assertThat(paged.pageUrl(2)).isEqualTo("/news/page-2/");
        assertThat(paged.pageUrl(12)).isEqualTo("/news/page-12/");

        PageUrlBuilder single = UrlPatternResolver.pageUrls("/about/", Map.of());
        assertThat(single.firstPageUrl()).isEqualTo("/about/");
        assertThat(single.pageUrl(3)).isEqualTo("/about/");
    }

    @Test
    @DisplayName("URL → 产物路径（§7.1.2）")
    void artifactPath() {
        assertThat(UrlPatternResolver.toArtifactPath("/")).isEqualTo("index.html");
        assertThat(UrlPatternResolver.toArtifactPath("/about/")).isEqualTo("about/index.html");
        assertThat(UrlPatternResolver.toArtifactPath("/news/2026/03/12/post.html"))
                .isEqualTo("news/2026/03/12/post.html");
        assertThat(UrlPatternResolver.toArtifactPath("/feed.xml")).isEqualTo("feed.xml");
        assertThat(UrlPatternResolver.toArtifactPath("/robots.txt")).isEqualTo("robots.txt");
        assertThat(UrlPatternResolver.toArtifactPath("/search/shard-0.json"))
                .isEqualTo("search/shard-0.json");
    }

    @Test
    @DisplayName("模式串与占位符的诊断：paginates / placeholdersOf")
    void diagnosis() {
        assertThat(UrlPatternResolver.paginates("/news/page-{n}/")).isTrue();
        assertThat(UrlPatternResolver.paginates("/news/page-{pageNo}/")).isTrue();
        assertThat(UrlPatternResolver.paginates("/about/")).isFalse();
        assertThat(UrlPatternResolver.placeholdersOf("/{categoryPath}/{slug}-{n}.html"))
                .containsExactly("categoryPath", "slug", "n");
    }

    @Test
    @DisplayName("真实形态回归：站点选项与类型定义里的全部 URL 规则都能算出第 1 页")
    void realWorldPatterns() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("categoryPath", "news");
        ctx.put("tagSlug", "java");
        ctx.put("facetPath", "brand-acme");
        ctx.put("year", 2026);
        ctx.put("month", 3);

        assertThat(UrlPatternResolver.resolve("/tag/{tagSlug}/", ctx, 1)).isEqualTo("/tag/java/");
        assertThat(UrlPatternResolver.resolve("/tags/", ctx, 1)).isEqualTo("/tags/");
        assertThat(UrlPatternResolver.resolve("/archive/{year}/{month}/", ctx, 1)).isEqualTo("/archive/2026/03/");
        assertThat(UrlPatternResolver.resolve("/search/", ctx, 1)).isEqualTo("/search/");
        assertThat(UrlPatternResolver.resolve("/thanks/", ctx, 1)).isEqualTo("/thanks/");
        assertThat(UrlPatternResolver.resolve("/f/{facetPath}/", ctx, 1)).isEqualTo("/f/brand-acme/");
        assertThat(UrlPatternResolver.resolve("/page-{n}/", ctx, 1)).isEqualTo("/");
        assertThat(UrlPatternResolver.resolve("/page-{n}/", ctx, 3)).isEqualTo("/page-3/");
        assertThat(UrlPatternResolver.resolve("/{categoryPath}/page-{n}/", ctx, 1)).isEqualTo("/news/");
        assertThat(UrlPatternResolver.resolve("/book/{parentSlug}/{slug}.html",
                Map.of("parentSlug", "b", "slug", "c1"), 1)).isEqualTo("/book/b/c1.html");
    }
}
