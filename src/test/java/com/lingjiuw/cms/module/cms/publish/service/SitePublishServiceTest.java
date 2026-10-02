package com.lingjiuw.cms.module.cms.publish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.tag.ArchiveTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.BreadcrumbTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ChannelTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.DetailTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ElseTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ForeachTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.FormTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IfTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IncludeTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ListTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.PagelistTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.PrenextTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.QueryTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.TagnavTag;
import com.lingjiuw.cms.module.cms.publish.template.validate.AnchorOfValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.DetailTypeValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.DisabledPageTypeWarner;
import com.lingjiuw.cms.module.cms.publish.template.validate.FieldPathValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.FilterFieldValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.FormCodeValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.NamedQueryValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationBodyValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationKindValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ParamValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ReferenceValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.StructureValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.TagNameValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.TagPageTypeMatrixValidator;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * **期 2 的端到端验收**：{@link SitePublishService#publish} 把一个真实主题 + 一份内存内容库变成
 * 一整套静态站（static-publish.md §8.1 的九个阶段全都跑到）。
 *
 * <p>它和 {@code MiniNewsSiteTest} 的分工：那个证明"14 个标签 + 编译器 + 渲染器能一起工作"，
 * 本用例证明**发布引擎**能一起工作——计划（§7.2.1）、URL 规则（§7.1）、产物落盘（§7.2.2）、
 * 聚合产物与 GC（§7.6、§8.6）、幂等（§8.4、§8.5 幂等）。因此它不 mock 任何东西：
 * 走真的 {@code FileTemplateSource}（主题从测试资源复制到 {@code @TempDir} 的
 * {@code template/e2e/}）、真的 {@code SitePlanner}、真的写盘。
 *
 * <p>三处刻意的构造决定（都是"契约 + 引擎实际行为"推出来的，不是随手写的）：
 * <ol>
 *   <li><b>{@code recordTask=false}</b>：不写 {@code cms_publish_task}，因此不需要数据库；
 *       {@code taskMapper} 传 {@code null} 也不会被用到（{@code SitePublishService#publish} 只在
 *       {@code recordTask=true} 时才碰它）。</li>
 *   <li><b>文章类型的 {@code list_url_pattern} 是 {@code /article/page-{n}/}，不是
 *       {@code /news/page-{n}/}</b>：分类索引页用站点选项 {@code url.list='/{categoryPath}/page-{n}/'}，
 *       对 {@code news} 分类算出的第 1 页正是 {@code /news/}——两个页面类型（类型列表页、分类索引页）
 *       会落到同一个 {@code news/index.html}，计划期的冲突检测（§7.1.3）会直接报 E4004 让整站发不出去。
 *       引擎在这里是对的：那**确实**是两个页面抢一个产物文件。所以类型列表页挪到 {@code /article/}，
 *       分类索引页仍按 §7.2.1 用 {@code url.list}，两边都不牺牲。</li>
 *   <li><b>{@code tag_list.html} 里的 {@code {cms:list}} 把 {@code tag} 写成字面量</b>：TAGPAGE 上
 *       {@code {cms:list}} 的 {@code type} / {@code category} 缺省只对分类索引页成立（§7.2.1），
 *       引擎没有"当前标签"这个缺省（{@code QueryParams.resolve} 里没有它），因此一个通用的
 *       {@code tag_list.html} 只能写死一个 tag slug。这是测试夹具的如实做法，不是本用例的判据。</li>
 * </ol>
 */
@DisplayName("期 2 验收：全站静态化端到端（SitePublishService）")
class SitePublishServiceTest {

    private static final long SITE_ID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* ==================================================================== */
    /* Test A：整站发布（含分页、聚合产物、GC、幂等）                          */
    /* ==================================================================== */

    @Test
    @DisplayName("整站发布：页面/分页/标签/归档/聚合产物/GC/幂等 全部到位")
    void publishFullSite(@TempDir Path siteDir) throws IOException {
        /*
         * 软断言：一个批次里要核的东西太多（21 个计划页 + 7 种聚合产物 + GC + 幂等），任何一条失败都
         * 不该遮住其余的结论——"全部失败清单一次报出来"才是这份用例的主要产出。因此 Test A 用
         * SoftAssertions，末尾 assertAll() 一次性抛出。
         */
        SoftAssertions softly = new SoftAssertions();

        // 夹具主题 → <siteDir>/template/e2e/（引擎按 site.theme 找主题目录，§7.3）
        Path themeRoot = siteDir.resolve("template").resolve("e2e");
        copyTree(fixtureThemeRoot(), themeRoot);
        Path www = siteDir.resolve("www");

        SitePublishService service = service(provider(true));

        /* ---------------- 第一次发布：全量 ---------------- */
        SitePublishService.PublishResult first =
                service.publish(SITE_ID, siteDir, provider(true), SitePublishService.Mode.full, "manual", false);
        String firstReport = report(first);

        softly.assertThat(first.success())
                .as("第一次发布必须成功（success() = errors 为空且 failedPages = 0）；批次报告：\n%s", firstReport)
                .isTrue();
        softly.assertThat(first.failedPages())
                .as("failedPages 必须是 0；批次报告：\n%s", firstReport)
                .isZero();
        softly.assertThat(first.errors())
                .as("errors 必须为空（每一条都是某一页的渲染失败）；批次报告：\n%s", firstReport)
                .isEmpty();
        softly.assertThat(first.writtenPages())
                .as("全量模式必须真的写出页面（§8.1 第 ⑤ 步）；批次报告：\n%s", firstReport)
                .isPositive();

        /* ---------------- 页面产物 ---------------- */
        // 首页：第 1 页按 publishTime desc 排，最新的是 tech 分类下的两篇（t2 / t1）
        assertArtifact(softly, www, "index.html");
        softly.assertThat(read(www, "index.html"))
                .as("首页第 1 页必须含最新一篇文章的标题")
                .contains("技术第二篇");
        // 首页的分页由站点选项 url.home（默认 /page-{n}/）决定（§7.1.3）
        assertArtifact(softly, www, "page-2/index.html");

        // 详情页路径 = 类型的 detail_url_pattern '/{categoryPath}/{slug}.html'（§7.1.1）
        assertArtifact(softly, www, "news/a1.html");
        String detail = read(www, "news/a1.html");
        softly.assertThat(detail).as("详情页正文").contains("第一篇");
        softly.assertThat(detail)
                .as("§5.2.1：RICHTEXT 字段 raw=1，正文必须原样输出，不能被转义成 &lt;p&gt;")
                .contains("<p>正文第一篇</p>")
                .doesNotContain("&lt;p&gt;");

        // 子分类内容的 URL 含两级 categoryPath：/news/tech/t1.html
        assertArtifact(softly, www, "news/tech/t1.html");

        // 分类索引页：§7.2.1 的"分类来源"用站点选项 url.list='/{categoryPath}/page-{n}/'
        // 子分类的产物路径是 tech/index.html 而不是 news/tech/index.html：SitePlanner.pathOf() 优先取
        // 分类节点的 path 字段，取不到才退回 slug，而 TestContentProvider.categories() 给出的节点上没有
        // path（只有 category(idOrSlug) 会补）——真实现 DbContentProvider 是带 path 的
        // （DbContentProvider.java:608/615/1321/1327）。夹具因此按"实际产物路径"断言。
        assertArtifact(softly, www, "news/index.html");
        assertArtifact(softly, www, "tech/index.html");
        softly.assertThat(read(www, "tech/index.html"))
                .as("子分类索引页要列出它自己的两篇内容")
                .contains("技术第一篇");
        // 分页：news 下 7 篇 / row=2 → 第 2 页 = 第 5、4 篇（派生页，§5.4）
        //
        // 【引擎缺陷，2026-10-02 现场】分类索引页的第 2..N 页现在渲染不出来。链条：
        //   ① SitePublishService.derived()（:332-338）把**所有**派生页的 pageType 一律改成 DPAGE
        //      （目的是让它们不进 sitemap，这一条本身是对的）；
        //   ② 但 §7.2.1 的"分类索引页缺省"（type='all' + category=当前分类）只挂在
        //      `ctx.pageType().matrixColumn() == LIST` 上（QueryParams.java:84），而
        //      DPAGE.matrixColumn() 是 DETAIL；
        //   ③ 于是第 2..N 页上的 {cms:list} 报 E1002「参数 type 不可用 ·
        //      当前页面（DPAGE）没有内容类型」，整个 /news/ 计划项（含它的 3 个派生页）都不产出。
        // 模板侧绕不过去：category 必须来自当前栏目，而参数不做字段插值（§3.3），
        // 所以"分类索引页 + 分页"这个组合在现引擎上无解。断言照契约保留。
        assertArtifact(softly, www, "news/page-2/index.html");
        softly.assertThat(read(www, "news/page-2/index.html"))
                .as("分类索引页第 2 页必须列出第 5、4 篇（publishTime desc），而不是第 1 页的重放")
                .contains("第五篇")
                .contains("第四篇")
                .doesNotContain("技术第二篇");

        // 类型列表页（类型来源用类型的 list_url_pattern='/article/page-{n}/'）
        assertArtifact(softly, www, "article/index.html");
        assertArtifact(softly, www, "article/page-2/index.html");

        // 标签总览（url.tags）与标签详情（url.tag）
        assertArtifact(softly, www, "tags/index.html");
        assertArtifact(softly, www, "tag/java/index.html");
        assertArtifact(softly, www, "tag/ops/index.html");

        // 归档页（url.archive='/{year}/{month}/'，archive.mode='month'）
        assertArtifact(softly, www, "archive/2026/03/index.html");

        // 筛选落地页（§7.4：只出 facets 里 page=true 的取值，count>0）
        assertArtifact(softly, www, "f/level-basic/index.html");

        // 单页（SINGLE 类型 about 的 detail_url_pattern='/about/'）
        assertArtifact(softly, www, "about/index.html");
        softly.assertThat(read(www, "about/index.html")).as("单页正文").contains("关于我们");

        // 搜索页壳（§7.5 第（4）条：只管壳 + 表单 + 结果容器；param.q 由 §7.2.3 注入）
        assertArtifact(softly, www, "search/index.html");
        softly.assertThat(read(www, "search/index.html"))
                .as("搜索页壳必须带表单，参数回显走 [field:param.q/]（§7.2.3、§7.5）")
                .contains("name=\"q\"")
                .contains("data-cms-search");
        // 404（url 固定 /404.html）
        assertArtifact(softly, www, "404.html");
        // 站点选项 pages.static 声明的静态页
        assertArtifact(softly, www, "thanks/index.html");

        /* ---------------- 聚合产物（§7.2.2） ---------------- */
        // 主题资源从 template/e2e/assets/ 复制到 www/assets/
        assertArtifact(softly, www, "assets/site.css");
        softly.assertThat(read(www, "assets/site.css")).as("主题资源必须原样复制").contains("e2e fixture theme stylesheet");

        // robots.txt：主题提供了就用主题的（§7.6），因此里面必须有主题那两行标记
        assertArtifact(softly, www, "robots.txt");
        String robots = read(www, "robots.txt");
        softly.assertThat(robots).as("robots.txt 必须来自主题（而不是引擎内置默认值）").contains("e2e fixture theme robots.txt");
        softly.assertThat(robots).as("robots.txt 必须指向 sitemap").contains("Sitemap:");

        // redirects.conf：本期表还没建，出的是一个合法的空文件（§7.6）
        assertArtifact(softly, www, "redirects.conf");

        // sitemap：**索引** sitemap.xml 指向分片，条目在分片里（§7.2.2 的"生成者"列写死：
        // sitemap 索引 + sitemap 分片是两种产物）——因此文章 URL 要在 sitemap-0.xml 里找。
        assertArtifact(softly, www, "sitemap.xml");
        assertArtifact(softly, www, "sitemap-0.xml");
        softly.assertThat(read(www, "sitemap.xml"))
                .as("sitemap.xml 是索引，必须指向分片")
                .contains("<sitemapindex").contains("sitemap-0.xml");
        softly.assertThat(read(www, "sitemap-0.xml"))
                .as("sitemap 分片必须收录详情页的绝对 URL（§7.6）")
                .contains("https://example.com/news/a1.html")
                .contains("https://example.com/news/tech/t1.html")
                .contains("<loc>https://example.com/</loc>")
                .contains("<loc>https://example.com/news/</loc>");
        // §7.6："只收录 S 类页面"，且"默认 noindex 的页面类型：FACET、DPAGE 的第 2..N 页、SEARCH、404"
        // ——sitemap 与 noindex 必须由同一个判定函数产出，这几条是"两处一致"的直接判据。
        softly.assertThat(read(www, "sitemap-0.xml"))
                .as("默认 noindex 的页面不能进 sitemap（§7.6）：派生页 / 筛选页 / 搜索页 / 404")
                .doesNotContain("/page-2/")
                .doesNotContain("/f/level-basic/")
                .doesNotContain("/search/")
                .doesNotContain("/404.html");
        // §7.6 的 noindex 传播：引擎把 page.noindex / page.robots 算好，模板照抄（header 片段里就是那一行）
        softly.assertThat(read(www, "404.html")).as("404 必须带 noindex（§7.6）").contains("noindex,follow");
        softly.assertThat(read(www, "page-2/index.html"))
                .as("第 2..N 页默认 noindex（§7.6，seo.paginatedIndex 默认为 0）")
                .contains("noindex,follow");
        softly.assertThat(read(www, "index.html"))
                .as("首页是 S 类可收录页面，不能出现 noindex")
                .doesNotContain("noindex");
        softly.assertThat(first.warnings())
                .as("批次报告里不该有「404 页被标成了可收录」这类 noindex 不一致的警告（§7.6）；批次报告：\n%s",
                        firstReport)
                .noneMatch(warning -> warning.contains("/404.html"));

        // feed.xml：由 SiteOutputs 生成（§7.6），站点名进标题
        assertArtifact(softly, www, "feed.xml");
        softly.assertThat(read(www, "feed.xml")).as("feed 必须含站点名").contains("端到端测试站");

        // 搜索索引（§7.5）：清单 + 分片
        assertArtifact(softly, www, "search/index.json");
        assertArtifact(softly, www, "search/shard-0.json");
        JsonNode searchIndex = jsonOf(www.resolve("search/index.json"));
        softly.assertThat(searchIndex.path("count").asInt())
                .as("搜索索引条数 = 7 篇 article（about 是 SINGLE，不入索引）")
                .isEqualTo(7);
        softly.assertThat(searchIndex.path("shards").size()).isEqualTo(1);

        // cms-site.json：最后写（它出现代表本批次产物已完整，§8.5）
        assertArtifact(softly, www, "cms-site.json");
        JsonNode marker = jsonOf(www.resolve("cms-site.json"));
        softly.assertThat(marker.path("siteCode").asText())
                .as("cms-site.json 必须带 siteCode（§7.6、§11.1）")
                .isEqualTo("e2e");
        softly.assertThat(marker.path("siteId").asLong()).isEqualTo(SITE_ID);

        // 批次清单：.publish/manifest.json（§7.2.2 最后一行：不在 www/ 下）
        Path manifestFile = siteDir.resolve(".publish").resolve("manifest.json");
        softly.assertThat(manifestFile).as("批次清单必须落在 <站点目录>/.publish/manifest.json").isRegularFile();
        JsonNode manifest = jsonOf(manifestFile);
        softly.assertThat(manifest.path("pages").isArray()).as("manifest.json 的 pages 必须是数组").isTrue();
        softly.assertThat(manifest.path("pages").size()).as("manifest.json 的 pages 不能为空").isPositive();
        softly.assertThat(manifest.path("pages").size())
                .as("清单条目数必须 >= 计划页数（派生页也进清单，GC 才认得它们）")
                .isGreaterThanOrEqualTo(first.totalPages());

        // GC 只删"历史上由引擎写出过"的东西（§8.6 第 3 行）：手写进 www/ 的文件必须活下来
        Path manual = www.resolve("extras/manual.txt");
        Files.createDirectories(manual.getParent());
        Files.writeString(manual, "手工文件，不是引擎产出\n", StandardCharsets.UTF_8);

        /* ---------------- 第二次发布：同一输入，验幂等（§8.4、§8.5） ---------------- */
        Map<String, String> afterFirst = hashArtifacts(www, Set.of());
        SitePublishService.PublishResult second =
                service.publish(SITE_ID, siteDir, provider(true), SitePublishService.Mode.full, "manual", false);
        String secondReport = report(second);
        softly.assertThat(second.errors())
                .as("第二次发布（同一输入）也不能报错；批次报告：\n%s", secondReport)
                .isEmpty();
        // 派生页（第 2..N 页）不在"页面计划"里、只在 manifest 里，而 GC 的输入必须是"本批次写过的
        // 全部路径"（含派生页）：否则同一批次刚写出的派生页会被自己删掉，站点的分页链接第二天全 404。
        softly.assertThat(www.resolve("page-2/index.html"))
                .as("第二次发布后首页第 2 页必须还在（§5.4 派生页不能被 §8.6 的 GC 自删）")
                .isRegularFile();
        softly.assertThat(www.resolve("article/page-3/index.html"))
                .as("第二次发布后类型列表页第 3 页必须还在")
                .isRegularFile();
        Map<String, String> afterSecond = hashArtifacts(www, Set.of());

        /* ---------------- 第三次发布：删掉 a1，验 GC（§8.6） ---------------- */
        SitePublishService.PublishResult third =
                service.publish(SITE_ID, siteDir, provider(false), SitePublishService.Mode.full, "manual", false);
        String thirdReport = report(third);
        softly.assertThat(third.success())
                .as("删掉一篇内容后的发布必须成功；批次报告：\n%s", thirdReport)
                .isTrue();
        softly.assertThat(third.deletedArtifacts())
                .as("内容被删 → 它的产物不在计划里但在旧清单里（§8.6 第 2 行），必须被删掉；批次报告：\n%s", thirdReport)
                .isPositive();
        softly.assertThat(www.resolve("news/a1.html"))
                .as("被删内容的详情页产物必须消失（幽灵页，§8.6）")
                .doesNotExist();
        softly.assertThat(www.resolve("news/a2.html"))
                .as("没被删的内容的产物必须留着")
                .isRegularFile();
        softly.assertThat(www.resolve("page-4/index.html"))
                .as("内容从 7 篇变 6 篇 → 首页第 4 页不再进计划，也要被 GC 删掉")
                .doesNotExist();
        softly.assertThat(www.resolve("extras/manual.txt"))
                .as("§8.6 第 3 行：www/ 下「不在计划里、也不是引擎产出过」的文件不许碰")
                .isRegularFile();

        /* ---------------- 幂等（§8.4 兜底、§8.5 渲染确定性） ---------------- */

        // (a) 页面产物：同一输入渲染两次必须逐字节相同（§12.3 第 2/3 条要的就是这一条）
        Map<String, String> firstPages = subset(afterFirst, name -> name.endsWith(".html"));
        Map<String, String> secondPages = subset(afterSecond, name -> name.endsWith(".html"));
        softly.assertThat(firstPages).as("第一次发布一个页面都没写出来，夹具不对").isNotEmpty();
        softly.assertThat(secondPages)
                .as("§8.5：同一输入重出全部页面，HTML 必须逐字节相同；不一致的页面：\n%s",
                        diff(firstPages, secondPages))
                .isEqualTo(firstPages);

        // (b) 严格要求（任务口径）：www/ 下除 cms-site.json 之外，每个文件的字节都必须一样
        //     （"发布是幂等的（同一输入产出同一结果）"，§8.4 (4)；排除 cms-site.json 的唯一理由是它
        //     的 publishedAt 是**设计上**的时间戳，§7.6）。
        //     这一条比 (a) 更严：sitemap.xml / sitemap-0.xml / search/index.json / feed.xml / robots.txt
        //     都在里面。引擎曾经把"生成时刻"写进 sitemap.xml 的 <lastmod>、search/index.json 的
        //     version 与 feed.xml 的 <lastBuildDate>，那时这条断言是红的——现在它们走"内容水位"
        //     （SiteOutputs.contentWatermark），因此这条断言把"改回去"钉死。
        Predicate<String> everythingButMarker = name -> !"cms-site.json".equals(name);
        softly.assertThat(subset(afterSecond, everythingButMarker))
                .as("§8.4/§8.5：www/ 下除 cms-site.json 之外必须逐字节相同"
                        + "（含 sitemap / feed / 搜索索引 / robots）；不一致的文件：\n%s",
                        diff(subset(afterFirst, everythingButMarker), subset(afterSecond, everythingButMarker)))
                .isEqualTo(subset(afterFirst, everythingButMarker));

        softly.assertAll();
    }

    /* ==================================================================== */
    /* Test B：计划期 URL 冲突 → E4004                                        */
    /* ==================================================================== */

    @Test
    @DisplayName("计划期 URL 冲突：两个类型 + 同名 slug → E4004")
    void planDetectsUrlConflict() throws IOException {
        /*
         * 构造（选"两个类型 + 同名 slug"这一种，理由：它只依赖 URL 规则本身，不需要凑分类/单页）：
         *   · 两个内容类型 article / note 都用同一个 detail_url_pattern='/{slug}.html'——这个模式里
         *     没有 {typeCode}，因此**类型不影响 URL**；
         *   · 两个类型各有一条 slug 相同（a1）的内容；
         *   · 计划期会把两条内容都映射成 /a1.html，也就是两个页面抢同一个产物文件。
         * 这正是 §7.1.3 / §8.2 要防的那件事："宁可不发布，也不发布一个被覆盖的页面"，
         * 判定落在 SitePlanner.detectConflicts()，错误码 E4004。
         *
         * note 类型的 detail_template 显式指向 article_detail.html，否则它会因为找不到
         * note_detail.html / detail.html 而被 E4002 跳过（跳过就没有冲突可报了）。
         */
        ContentTypeDef article = new ContentTypeDef(1L, "article", "文章", ContentTypeDef.Kind.CONTENT, false,
                "/{slug}.html", null, "article_detail.html", null, null,
                "publishTime", "desc", 20, null, null, List.of(), Map.of());
        ContentTypeDef note = new ContentTypeDef(2L, "note", "笔记", ContentTypeDef.Kind.CONTENT, false,
                "/{slug}.html", null, "article_detail.html", null, null,
                "publishTime", "desc", 20, null, null, List.of(), Map.of());

        ContentProvider provider = TestContentProvider.builder()
                .site(siteConfig())
                .type(article)
                .type(note)
                .content(TestContentProvider.ContentSpec.of(1L, "article", "a1", "文章 A")
                        .publishTime("2026-03-01 10:00:00"))
                .content(TestContentProvider.ContentSpec.of(2L, "note", "a1", "笔记 A")
                        .publishTime("2026-03-02 10:00:00"))
                .build();

        SitePlanner planner = new SitePlanner(provider,
                new ThemeTemplateLookup(new FileTemplateSource(fixtureThemeRoot()), "e2e"));

        Throwable thrown = catchThrowable(planner::plan);

        assertThat(thrown)
                .as("两个页面映射到同一个产物路径时必须由计划期拦下（§7.1.3、§8.2 的 detectConflicts）")
                .isInstanceOf(PublishException.class);
        PublishException failure = (PublishException) thrown;
        assertThat(failure.code()).as("错误码必须是 E4004（URL 冲突）").isEqualTo(PublishErrorCode.E4004);
        assertThat(failure.getMessage())
                .as("报错文案要带错误码与「URL 冲突」这几个字（§10.1 的三行格式）")
                .contains("E4004")
                .contains("URL 冲突");
        assertThat(failure.getMessage())
                .as("文案要指出冲突的产物路径，模板/内容作者才知道改哪一个")
                .contains("/a1.html");
    }

    /* ==================================================================== */
    /* 夹具：站点、类型、内容、服务、主题                                      */
    /* ==================================================================== */

    private static SiteConfig siteConfig() {
        return new SiteConfig(SITE_ID, "e2e", "端到端测试站", "example.com", "https",
                "/assets/logo.png", "zh-CN", "端到端发布测试站点", "端到端,测试", "端到端发布测试站点",
                "京ICP备00000001号", "010-00000001", "e2e@example.com",
                "sites/e2e/www", "/assets/cover.png", "/assets/og.png",
                "e2e", "", "", Map.of());
    }

    /**
     * 2 个内容类型 / 2 个分类（news 根 + tech 子）/ 2 个标签 / 1 个菜单 / 8 条内容的完整站点。
     *
     * @param withA1 {@code false} = 少一条内容（news/a1），用于验 GC（§8.6）
     */
    private static TestContentProvider provider(boolean withA1) {
        TestContentProvider.Builder builder = TestContentProvider.builder()
                .now(LocalDateTime.of(2026, 6, 1, 12, 0))
                .site(siteConfig())
                // —— 站点发布选项（§2.7）：页面类型开关 + 四条 URL 规则 ——
                .siteOption("page.category", 1)
                .siteOption("page.tag", 1)
                .siteOption("page.taglist", 1)
                .siteOption("page.archive", 1)
                .siteOption("page.facet", 1)
                .siteOption("page.search", 1)
                .siteOption("page.feed", 1)
                .siteOption("page.tagMinCount", 1)
                .siteOption("url.home", "/page-{n}/")
                .siteOption("url.list", "/{categoryPath}/page-{n}/")
                .siteOption("url.tag", "/tag/{tagSlug}/")
                .siteOption("url.tags", "/tags/")
                .siteOption("url.archive", "/archive/{year}/{month}/")
                .siteOption("url.search", "/search/")
                .siteOption("url.facet", "/f/{facetPath}/")
                .siteOption("archive.mode", "month")
                .siteOption("feed.types", List.of("article"))
                .siteOption("feed.size", 10)
                .siteOption("search.mode", "static")
                // pages.static：站点声明的静态页（§7.2.1 第 11 行）
                .siteOption("pages.static", List.of(Map.of(
                        "code", "thanks", "url", "/thanks/", "template", "thanks.html")))
                // —— 类型定义（§2.1）——
                .type(articleType())
                .type(aboutType())
                .enumField("article", "level", "basic:基础", "pro:进阶")
                // —— 分类与标签（§2.4）——
                .category(1L, 0L, "news", "新闻")
                .category(2L, 1L, "tech", "科技")
                .tag(1L, "java", "Java")
                .tag(2L, "ops", "运维")
                .menu("main", List.of(
                        NavItem.of(nav(1L, "首页", "/")),
                        NavItem.of(nav(2L, "新闻", "/news/")),
                        NavItem.of(nav(3L, "科技", "/news/tech/")),
                        NavItem.of(nav(4L, "关于我们", "/about/")),
                        NavItem.of(nav(5L, "感谢页", "/thanks/"))))
                /*
                 * 内容的 url 字段（列表里的链接、feed、搜索索引都用它）。真实现里由数据层的
                 * UrlResolver 按类型的 detail_url_pattern 算（§7.1），这里的内存放不下分类路径，
                 * 于是按 id 写死一份与 detail_url_pattern 完全一致的映射——夹具的链接必须能点开，
                 * 否则这个主题就不是"完整可用"的。
                 */
                .itemUrl(item -> switch ((int) item.id()) {
                    case 6, 7 -> "/news/tech/" + item.slug() + ".html";
                    case 8 -> "/about/";
                    default -> "/news/" + item.slug() + ".html";
                });

        if (withA1) {
            builder.content(article(1L, "a1", "第一篇", "2026-03-01 10:00:00", "news", "java", "basic", 100L));
        }
        return builder
                .content(article(2L, "a2", "第二篇", "2026-03-02 10:00:00", "news", "java", "basic", 50L))
                .content(article(3L, "a3", "第三篇", "2026-03-03 10:00:00", "news", "ops", "basic", 10L))
                .content(article(4L, "a4", "第四篇", "2026-03-04 10:00:00", "news", null, null, 5L))
                .content(article(5L, "a5", "第五篇", "2026-03-05 10:00:00", "news", null, null, 1L))
                .content(article(6L, "t1", "技术第一篇", "2026-03-06 10:00:00", "tech", null, null, 3L))
                .content(article(7L, "t2", "技术第二篇", "2026-03-07 10:00:00", "tech", null, null, 2L))
                .content(TestContentProvider.ContentSpec.of(8L, "about", "about", "关于我们")
                        .publishTime("2026-02-01 10:00:00")
                        .content("<p>关于我们</p>"))
                .build();
    }

    /** article：CONTENT，详情 URL 含分类路径，列表 URL 用类型自己的规则，per_page=2（分页才真实）。 */
    private static ContentTypeDef articleType() {
        return new ContentTypeDef(101L, "article", "文章", ContentTypeDef.Kind.CONTENT, false,
                "/{categoryPath}/{slug}.html", "/article/page-{n}/", null, null, null,
                "publishTime", "desc", 2, null, null, List.of(),
                // §7.4：筛选取值条 = 字段的 options；只有 count>0 的取值才出落地页
                Map.of("facets", List.of(Map.of("field", "level", "page", true, "label", "等级"))));
    }

    /** about：SINGLE，URL 写死 /about/（§7.2.1 第 8 行）。 */
    private static ContentTypeDef aboutType() {
        return new ContentTypeDef(102L, "about", "关于我们", ContentTypeDef.Kind.SINGLE, false,
                "/about/", null, null, null, null,
                "publishTime", "desc", 20, null, null, List.of(), Map.of());
    }

    private static TestContentProvider.ContentSpec article(long id, String slug, String title,
                                                          String publishTime, String category, String tag,
                                                          String level, long viewCount) {
        TestContentProvider.ContentSpec spec = TestContentProvider.ContentSpec.of(id, "article", slug, title)
                .publishTime(publishTime)
                .summary("摘要：" + title)
                .author("9", "张三", "zhangsan")
                .viewCount(viewCount)
                .content("<p>正文" + title + "</p>");
        if (category != null) {
            spec.category(category);
        }
        if (tag != null) {
            spec.tag(tag);
        }
        if (level != null) {
            spec.field("level", List.of(level));
        }
        return spec;
    }

    private static Map<String, Object> nav(long id, String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("url", url);
        return item;
    }

    /** 按 Spring 的注入方式组装引擎：全部标签 + 全部编译期校验器（少一个就不是端到端）。 */
    private static SitePublishService service(ContentProvider provider) {
        // 显式给出 List 的类型实参：查询标签的公共父类 AbstractQueryTag 是包级私有的，
        // 隐式推断要算 LUB 会报"无法访问 AbstractQueryTag"。
        TagRegistry registry = new TagRegistry(List.<TagHandler>of(
                new IncludeTag(), new IfTag(), new ElseTag(), new ForeachTag(),
                new ListTag(provider), new QueryTag(provider), new DetailTag(provider),
                new PagelistTag(provider), new ChannelTag(provider), new BreadcrumbTag(provider),
                new PrenextTag(provider), new TagnavTag(provider), new ArchiveTag(provider),
                new FormTag(provider)));

        List<TemplateValidator> validators = List.of(
                new TagNameValidator(registry), new ParamValidator(registry), new StructureValidator(registry),
                new FieldPathValidator(registry), new FilterFieldValidator(registry),
                new ReferenceValidator(registry), new PaginationBodyValidator(registry),
                new PaginationKindValidator(), new DetailTypeValidator(registry), new AnchorOfValidator(),
                new FormCodeValidator(), new NamedQueryValidator(registry),
                new TagPageTypeMatrixValidator(registry), new DisabledPageTypeWarner());

        // taskMapper 传 null：本用例一律 recordTask=false，引擎不会碰它（因此不需要数据库）
        return new SitePublishService(registry, validators, null);
    }

    /** 夹具主题在测试资源里的根目录（{@code src/test/resources/themes/e2e}）。 */
    private static Path fixtureThemeRoot() {
        URL url = SitePublishServiceTest.class.getClassLoader().getResource("themes/e2e");
        assertThat(url).as("夹具主题 themes/e2e 必须在测试资源里").isNotNull();
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("测试主题路径不可解析：" + url, e);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /* ==================================================================== */
    /* 断言与诊断助手                                                         */
    /* ==================================================================== */

    /** 一个产物必须存在；不存在时把 {@code www/} 下现有的东西全列出来。 */
    private static void assertArtifact(SoftAssertions softly, Path www, String relative) {
        softly.assertThat(www.resolve(relative))
                .as("产物 %s 必须存在（§7.2.2 的产物清单）；www/ 下现有：%s", relative, listing(www))
                .isRegularFile();
    }

    /**
     * 读一个产物文本；**文件不存在时返回空串**而不是抛异常——否则一条"产物缺失"会让整份用例在
     * 这里中断，后面的 GC 与幂等断言就没有结论了（软断言要的是"一次报全"）。
     */
    private static String read(Path www, String relative) {
        Path file = www.resolve(relative);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读产物失败：" + file, e);
        }
    }

    /** 读一个 JSON 产物；缺失或解析不了时返回 MissingNode（断言会以"值不对"的方式失败，不抛异常）。 */
    private static JsonNode jsonOf(Path file) {
        if (!Files.isRegularFile(file)) {
            return MissingNode.getInstance();
        }
        try {
            return MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return MissingNode.getInstance();
        }
    }

    /** {@code www/} 下全部文件的相对路径（失败诊断用）。 */
    private static String listing(Path www) {
        if (!Files.isDirectory(www)) {
            return "（www/ 还不存在）";
        }
        try (Stream<Path> stream = Files.walk(www)) {
            String text = stream.filter(Files::isRegularFile)
                    .map(path -> www.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.joining(" "));
            return text.isEmpty() ? "（一个文件都没有）" : text;
        } catch (IOException e) {
            return "（列产物失败：" + e.getMessage() + "）";
        }
    }

    /** 相对路径 → 内容 SHA-256；{@code .tmp} 跳过（原子写的临时文件，§8.5）。 */
    private static Map<String, String> hashArtifacts(Path www, Set<String> exclude) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(www)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String relative = www.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith(".tmp") || exclude.contains(relative)) {
                    continue;
                }
                hashes.put(relative, sha256(Files.readAllBytes(file)));
            }
        }
        return hashes;
    }

    private static Map<String, String> subset(Map<String, String> artifacts, Predicate<String> keep) {
        Map<String, String> result = new TreeMap<>();
        artifacts.forEach((name, hash) -> {
            if (keep.test(name)) {
                result.put(name, hash);
            }
        });
        return result;
    }

    /** 两次发布之间不一致的文件清单（只列相对路径与哈希前 12 位，避免刷屏）。 */
    private static String diff(Map<String, String> left, Map<String, String> right) {
        Set<String> names = new TreeSet<>(left.keySet());
        names.addAll(right.keySet());
        List<String> lines = new ArrayList<>();
        for (String name : names) {
            String before = left.get(name);
            String after = right.get(name);
            if (!Objects.equals(before, after)) {
                lines.add("  · " + name + "：" + shortHash(before) + " → " + shortHash(after));
            }
        }
        return lines.isEmpty() ? "（无差异）" : String.join("\n", lines);
    }

    private static String shortHash(String hash) {
        return hash == null ? "（文件缺失）" : hash.substring(0, 12);
    }

    /** 批次报告：断言失败时把计划数、写出数、警告、错误一次性打出来（排障只需要这一个字符串）。 */
    private static String report(SitePublishService.PublishResult result) {
        StringBuilder text = new StringBuilder();
        text.append("模式=").append(result.mode()).append("，主题=").append(result.theme())
                .append("，计划=").append(result.totalPages()).append(" 页")
                .append("，写出=").append(result.writtenPages())
                .append("，跳过=").append(result.skippedPages())
                .append("，失败=").append(result.failedPages())
                .append("，删除=").append(result.deletedArtifacts())
                .append("，聚合产物=").append(result.aggregateArtifacts())
                .append("，用时=").append(result.elapsedMillis()).append("ms");
        if (!result.errors().isEmpty()) {
            text.append("\nerrors（").append(result.errors().size()).append(" 条）：");
            result.errors().forEach(error -> text.append("\n  · ").append(error));
        }
        if (!result.warnings().isEmpty()) {
            text.append("\nwarnings（").append(result.warnings().size()).append(" 条）：");
            result.warnings().forEach(warning -> text.append("\n  · ").append(warning));
        }
        return text.toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16))
                        .append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }
}
