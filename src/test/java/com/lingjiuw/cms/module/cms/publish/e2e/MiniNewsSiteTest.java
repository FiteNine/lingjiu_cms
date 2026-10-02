package com.lingjiuw.cms.module.cms.publish.e2e;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateSource;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
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
import com.lingjiuw.cms.module.cms.publish.template.validate.FieldPathValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.FilterFieldValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.FormCodeValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationBodyValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationKindValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ParamValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ReferenceValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.StructureValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.TagNameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **期 1 的验收判据**（static-publish.md §12.2 期 1）：用真实的主题模板跑通一个最小资讯站
 * ——首页 + 分类页 + 详情页 + 分页 + 上下篇 + 面包屑。
 *
 * <p>它与各标签自己的单测是两件事：单测证明"这个标签按 §6.x 工作"，本用例证明
 * **14 个标签 + 编译器 + 渲染器 + 作用域栈 + include 展开能一起工作**——那才是"能发布一个
 * 最小资讯站"这句话的内容。所以这里不做任何 mock：走 {@code TemplateCompiler} 编译真实的
 * 主题文件（含 {@code {cms:include}} 展开），走 {@code DefaultTemplateRenderer} 渲染。
 *
 * <p>主题在 {@code src/test/resources/themes/mini/}，按 §7.3 的目录约定摆放
 * （{@code _partials/} 放片段，{@code index.html} / {@code list.html} / {@code detail.html}
 * 是三种页面类型的模板）。
 *
 * <p>断言是**语义断言**（标题顺序、URL、raw 正文没被转义、分页条内容、面包屑层级），
 * 不是逐字节比对：逐字节的那种在 {@link MiniNewsSiteGoldenTest} 里，两者分工不同——
 * 语义断言解释"为什么对"，黄金文件负责"有没有悄悄变"。
 */
@DisplayName("期 1 验收：最小资讯站端到端")
class MiniNewsSiteTest {

    private static final long SITE_ID = 1L;
    private static final String DOMAIN = "example.com";

    private TestContentProvider provider;
    private TemplateCompiler compiler;
    private DefaultTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        provider = TestContentProvider.builder()
                .now(LocalDateTime.of(2026, 3, 10, 0, 0))
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .perPage("article", 2)
                .sort("article", "publishTime", "desc")
                .field("article", "price", FieldType.DECIMAL)
                .field("article", "brand", FieldType.TEXT)
                .category(1, 0, "news", "新闻")
                .category(2, 1, "tech", "科技")
                .tag(1, "java", "Java")
                .menu("main", List.of(
                        NavItem.of(nav(1, "首页", "/")),
                        NavItem.of(nav(2, "新闻", "/news/")),
                        NavItem.of(nav(3, "关于我们", "/about/"))))
                .itemUrl(item -> "/news/" + item.slug() + ".html")
                .content(article(1, "a1", "第一篇", "2026-03-01 10:00:00"))
                .content(article(2, "a2", "第二篇", "2026-03-02 10:00:00"))
                .content(article(3, "a3", "第三篇", "2026-03-03 10:00:00"))
                .content(article(4, "a4", "第四篇", "2026-03-04 10:00:00"))
                .content(article(5, "a5", "第五篇", "2026-03-05 10:00:00"))
                .build();

        // 显式给出类型实参：`List.of(...)` 的隐式推断要算公共父类型（LUB），而查询标签的
        // 公共父类 `AbstractQueryTag` 是包级私有的（tag 包内部实现细节，不该为了测试放宽），
        // 隐式推断会因此报"无法访问 AbstractQueryTag"。写死 TagHandler 就绕开了 LUB 计算。
        TagRegistry registry = new TagRegistry(List.<TagHandler>of(
                new IncludeTag(), new IfTag(), new ElseTag(), new ForeachTag(),
                new ListTag(provider), new QueryTag(provider), new DetailTag(provider),
                new PagelistTag(provider), new ChannelTag(provider), new BreadcrumbTag(provider),
                new PrenextTag(provider), new TagnavTag(provider), new ArchiveTag(provider),
                new FormTag(provider)));

        List<TemplateValidator> validators = List.of(
                new TagNameValidator(registry), new ParamValidator(registry),
                new StructureValidator(registry), new FieldPathValidator(registry),
                new FilterFieldValidator(registry), new ReferenceValidator(registry),
                new PaginationBodyValidator(registry), new PaginationKindValidator(),
                new DetailTypeValidator(registry), new AnchorOfValidator(),
                new FormCodeValidator());

        compiler = new TemplateCompiler(new ThemeSource("themes/mini"), registry, validators);
        renderer = new DefaultTemplateRenderer(registry);
    }

    @Test
    @DisplayName("首页第 1 页：主列表 2 条 + 分页条 3 页 + 主导航当前项高亮")
    void homeFirstPage() {
        String html = renderHome(1, 3);

        assertThat(html).contains("<h1 class=\"site-title\">测试站</h1>");
        assertThat(html).contains("第五篇").contains("第四篇");
        assertThat(html).doesNotContain("第三篇");

        // 导航来自 {cms:channel source='menu'}，三项都在
        assertThat(html).contains(">首页<").contains(">新闻<").contains(">关于我们<");

        // 分页条：3 页 → 上一页/下一页/末页 + 页码；第 1 页不应有"首页"这一项之外的问题
        String pager = section(html, "<nav class=\"pager\">", "</nav>");
        assertThat(pager).contains("下一页").contains("末页");
        assertThat(pager).contains("href=\"/page-2/\"").contains("href=\"/page-3/\"");
        assertThat(pager).doesNotContain("href=\"/page-1/\"");
    }

    @Test
    @DisplayName("首页第 2 页：只渲染剩下的内容，页码切换生效")
    void homeSecondPage() {
        String html = renderHome(2, 3);

        assertThat(html).contains("第三篇").contains("第二篇");
        assertThat(html).doesNotContain("第五篇").doesNotContain("第一篇");

        String pager = section(html, "<nav class=\"pager\">", "</nav>");
        assertThat(pager).contains("上一页").contains("首页");
        assertThat(pager).contains("href=\"/page-3/\"");
    }

    @Test
    @DisplayName("分类页：面包屑到当前栏目 + channel.label + 分页主体")
    void categoryListPage() {
        RenderContext ctx = RenderContext.forPage(PageType.LIST, null, null);
        ctx.pushNamed("channel", categoryChannel());
        ctx.putPage("url", "/news/");
        ctx.putPage("title", "新闻");
        ctx.putPage("canonical", "https://" + DOMAIN + "/news/");
        ctx.putPage("noindex", false);
        ctx.putPage("robots", "index,follow");
        ctx.putPage("lastmod", "2026-03-10T00:00:00");
        ctx.putPage("pageNo", 1);
        ctx.putPage("paginationKind", "list");
        ctx.pageUrls(listPageUrls());
        ctx.fieldDefLookup(provider.fieldDefLookup());

        String html = render(ctx, "list.html");

        assertThat(html).contains("<h1 class=\"channel-title\">新闻</h1>");
        // 面包屑：起点（站点名）→ 当前分类
        String crumbs = section(html, "<nav class=\"breadcrumb\">", "</nav>");
        assertThat(crumbs).contains("测试站").contains("新闻");
        // 列表项 2 条（per_page=2），按 publishTime desc
        assertThat(html).contains("第五篇").contains("第四篇").doesNotContain("第三篇");
    }

    @Test
    @DisplayName("详情页：raw 正文没被转义 + 面包屑含栏目 + 上下篇各一条")
    void detailPage() {
        ContentItem entry = provider.content("article", 3);
        assertThat(entry).isNotNull();

        RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article", entry);
        ctx.pushNamed("channel", categoryChannel());
        ctx.putPage("url", "/news/a3.html");
        ctx.putPage("title", "第三篇");
        ctx.putPage("canonical", "https://" + DOMAIN + "/news/a3.html");
        ctx.putPage("noindex", false);
        ctx.putPage("robots", "index,follow");
        ctx.putPage("lastmod", "2026-03-03T10:00:00");
        ctx.pageUrls(PageUrlBuilder.single("/news/a3.html"));
        ctx.fieldDefLookup(provider.fieldDefLookup());

        String html = render(ctx, "detail.html");

        assertThat(html).contains("<h1 class=\"title\">第三篇</h1>");

        // §5.2.1：富文本字段 raw=1，正文必须原样输出——这是最容易被写成"转义"的一处
        String content = section(html, "<div class=\"content\">", "</div>");
        assertThat(content).contains("<p>正文第三篇</p>");
        assertThat(content).doesNotContain("&lt;p&gt;");

        // 面包屑：站点名 → 新闻 → 第三篇
        String crumbs = section(html, "<nav class=\"breadcrumb\">", "</nav>");
        assertThat(crumbs).contains("测试站").contains("新闻").contains("第三篇");

        // 上下篇：按 publishTime desc 的相邻两条 = 第四篇 / 第二篇
        String prenext = section(html, "<nav class=\"prenext\">", "</nav>");
        assertThat(prenext).contains("第四篇").contains("第二篇");
        assertThat(prenext).doesNotContain("第五篇").doesNotContain("第一篇");
    }

    @Test
    @DisplayName("同一模板换 pageNo 重渲染 = 派生页（§5.4 的派生页模型）")
    void derivedPageReusesSameTemplate() {
        String page1 = renderHome(1, 3);
        String page3 = renderHome(3, 3);

        assertThat(page1).isNotEqualTo(page3);
        assertThat(page3).contains("第一篇").doesNotContain("第五篇");
        // 只有 pageNo 不同，模板与上下文完全相同
        assertThat(page3).contains("<h1 class=\"site-title\">测试站</h1>");
    }

    @Test
    @DisplayName("黄金文件：固定主题 + 固定内容 → 期望 HTML 逐字节比较（§12.3 第 2 条）")
    void goldenHomePage() throws IOException {
        // 为什么必须钉住 site.year：SiteConfig.year() 取的是**当前年份**（§2.7 的派生 key），
        // 而 footer 片段会输出它。不覆盖的话这份黄金文件每到 1 月 1 日就红——那不是"产物变了"，
        // 是测试自己依赖了时钟。所以这里显式给一个固定年份。
        RenderContext ctx = homeContext(1, 3);
        Map<String, Object> site = new LinkedHashMap<>(provider.site().toScope());
        site.put("year", 2026);
        ctx.pushNamed("site", site);

        String html = render(ctx, "index.html");

        Path golden = Paths.get("src", "test", "resources", "golden", "mini-home.html");
        if (Boolean.getBoolean("cms.golden.write")) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, html, StandardCharsets.UTF_8);
            System.out.println("[golden] 已写出 " + golden.toAbsolutePath() + "（" + html.length() + " 字符）");
            return;
        }
        assertThat(Files.exists(golden))
                .as("黄金文件缺失：%s（首次冻结时用 -Dcms.golden.write=true 生成并人工 review）", golden)
                .isTrue();
        assertThat(html)
                .as("产物与黄金文件不一致——§8.5 要求渲染幂等，产物一变就该 red")
                .isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("渲染幂等：同一模板同一上下文渲染两次，产物逐字节相同（§8.5）")
    void renderIsIdempotent() {
        assertThat(renderHome(1, 3)).isEqualTo(renderHome(1, 3));
        assertThat(renderHome(2, 3)).isEqualTo(renderHome(2, 3));
    }

    /* ---------------- 夹具 ---------------- */

    private static Map<String, Object> nav(long id, String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("url", url);
        return item;
    }

    private static TestContentProvider.ContentSpec article(long id, String slug, String title, String publishTime) {
        return TestContentProvider.ContentSpec.of(id, "article", slug, title)
                .publishTime(publishTime)
                .category("news")
                .content("<p>正文" + title + "</p>");
    }

    private static Map<String, Object> categoryChannel() {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", 1L);
        channel.put("name", "新闻");
        channel.put("label", "新闻");
        channel.put("slug", "news");
        channel.put("url", "/news/");
        channel.put("path", "/news");
        channel.put("count", 5L);
        return channel;
    }

    private static PageUrlBuilder listPageUrls() {
        return new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return "/news/";
            }

            @Override
            public String pageUrl(int pageNo) {
                return pageNo <= 1 ? "/news/" : "/news/page-" + pageNo + "/";
            }
        };
    }

    /** 首页：站点选项 {@code url.home} 默认就是 {@code /page-{n}/}（§2.7）。 */
    private String renderHome(int pageNo, int totalPages) {
        return render(homeContext(pageNo, totalPages), "index.html");
    }

    /** 首页的渲染上下文；单独抽出来是为了让黄金文件用例能替换 {@code site} 作用域（见下）。 */
    private RenderContext homeContext(int pageNo, int totalPages) {
        RenderContext ctx = RenderContext.forPage(PageType.HOME, null, null);
        ctx.putPage("url", pageNo <= 1 ? "/" : "/page-" + pageNo + "/");
        ctx.putPage("title", "测试站");
        ctx.putPage("canonical", "https://" + DOMAIN + (pageNo <= 1 ? "/" : "/page-" + pageNo + "/"));
        ctx.putPage("noindex", false);
        ctx.putPage("robots", "index,follow");
        ctx.putPage("lastmod", "2026-03-10T00:00:00");
        ctx.putPage("pageNo", pageNo);
        ctx.putPage("totalPages", totalPages);
        ctx.putPage("paginationKind", "list");
        ctx.pageUrls(new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return "/";
            }

            @Override
            public String pageUrl(int page) {
                return page <= 1 ? "/" : "/page-" + page + "/";
            }
        });
        ctx.fieldDefLookup(provider.fieldDefLookup());
        return ctx;
    }

    private String render(RenderContext ctx, String template) {
        // §5.1 第（2）条：`site` 作用域**始终存在**，压它的是页面计划（期 2）。
        // 本用例扮演计划，所以在这里压一次——少了它，[field:site.lang/] 会在渲染期报 E1004。
        if (!ctx.hasNamed("site")) {
            ctx.pushNamed("site", provider.site().toScope());
        }
        CompileContext compileContext = new CompileContext(SITE_ID, ctx.pageType(), ctx.typeCode(), provider);
        TemplateAst ast = compiler.compile(template, compileContext);
        StringBuilder out = new StringBuilder();
        renderer.render(ast, ctx, out);
        return out.toString();
    }

    /** 取两个标记之间的一段，便于把断言限定在某个区块内（导航/分页/面包屑/正文）。 */
    private static String section(String html, String from, String to) {
        int start = html.indexOf(from);
        assertThat(start).as("找不到区块起点 %s", from).isGreaterThanOrEqualTo(0);
        int end = html.indexOf(to, start + from.length());
        assertThat(end).as("找不到区块终点 %s", to).isGreaterThan(start);
        return html.substring(start, end + to.length());
    }

    /** 从测试资源目录读主题模板；{@code file} 相对主题根（片段写在 {@code _partials/} 下）。 */
    static final class ThemeSource implements TemplateSource {

        private final Path root;

        ThemeSource(String resourceDir) {
            URL url = getClass().getClassLoader().getResource(resourceDir);
            if (url == null) {
                throw new IllegalStateException("测试主题不存在：" + resourceDir);
            }
            try {
                this.root = Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("测试主题路径不可解析：" + url, e);
            }
        }

        @Override
        public Template load(String relativePath) {
            Path file = root.resolve(relativePath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                return new Template(relativePath, source, Files.getLastModifiedTime(file).toMillis(),
                        Files.size(file));
            } catch (IOException e) {
                throw new IllegalStateException("读模板失败：" + relativePath, e);
            }
        }
    }
}
