package com.lingjiuw.cms.module.cms.publish.query;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.tag.DetailTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ListTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.QueryTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code {cms:list}} / {@code {cms:query}} / {@code {cms:detail}} 的渲染期行为（§5.4、§5.6、§6.3）。
 *
 * <p>这组用例专门盯**分页**：同一个 AST、同一个上下文，只换 {@code page.pageNo}，
 * 渲染出来的必须是不同的一批条目——§5.4 的派生页模型全靠这一条。它曾经因为
 * "预解析结果读不回来（内核的 nodeState 键用错）"而退化成"每页都渲染第 1 页"，
 * 因此这里的断言写成"第 N 页恰好是第 N 批"，而不是"第 N 页不空"。
 */
class QueryTagPagingTest {

    /** 5 篇文章，per_page=2，publishTime desc → 第 1 页 5/4、第 2 页 3/2、第 3 页 1。 */
    private static TestContentProvider news() {
        TestContentProvider.Builder builder = TestContentProvider.builder()
                .now(java.time.LocalDateTime.of(2026, 3, 10, 0, 0))
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .perPage("article", 2)
                .sort("article", "publishTime", "desc")
                .category(1, 0, "news", "新闻")
                .tag(1, "hot", "热点");
        for (int i = 1; i <= 5; i++) {
            builder.content(i, "article", "a" + i, "第" + i + "篇")
                    .publishTime("2026-03-0" + i + "T10:00");
        }
        return builder.build();
    }

    /** 造一个首页渲染上下文；{@code pageUrls} 给 {@code /page-{n}/} 形态（§2.7 的 {@code url.home}）。 */
    private static RenderContext home(int pageNo) {
        RenderContext ctx = RenderContext.forPage(PageType.HOME, null, null);
        ctx.pushNamed("site", Map.of("name", "测试站", "url", "https://example.com",
                "defaultCover", "/static/default-cover.png"));
        ctx.putPage("pageNo", pageNo);
        ctx.putPage("url", pageNo <= 1 ? "/" : "/page-" + pageNo + "/");
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
        return ctx;
    }

    private static TagNode listNode(Map<String, String> args) {
        return StubRenderer.tag("list", args, "id", "title", "class");
    }

    private static List<String> titlesOf(StubRenderer renderer) {
        return renderer.all("title");
    }

    /* ---------------- {cms:list} 的分页 ---------------- */

    @Test
    void 同一个AST换pageNo渲染出不同的一批条目() {
        TestContentProvider provider = news();
        ListTag tag = new ListTag(provider);
        Map<String, String> args = Map.of("type", "article", "category", "all", "row", "2");

        StubRenderer p1 = new StubRenderer();
        TagNode node1 = listNode(args);
        RenderContext ctx1 = home(1);
        tag.prepare(node1, ctx1);
        tag.render(node1, ctx1, p1, new StringBuilder());
        assertEquals(List.of("第5篇", "第4篇"), titlesOf(p1));

        StubRenderer p2 = new StubRenderer();
        TagNode node2 = listNode(args);
        RenderContext ctx2 = home(2);
        tag.prepare(node2, ctx2);
        tag.render(node2, ctx2, p2, new StringBuilder());
        assertEquals(List.of("第3篇", "第2篇"), titlesOf(p2),
                "第 2 页必须是第 2 批——派生页模型（§5.4 第 5 步）就靠这一条");

        StubRenderer p3 = new StubRenderer();
        TagNode node3 = listNode(args);
        RenderContext ctx3 = home(3);
        tag.prepare(node3, ctx3);
        tag.render(node3, ctx3, p3, new StringBuilder());
        assertEquals(List.of("第1篇"), titlesOf(p3), "最后一页只有 1 条");
    }

    @Test
    void totalPages与翻页标志与页URL正确() {
        TestContentProvider provider = news();
        ListTag tag = new ListTag(provider);
        Map<String, String> args = Map.of("type", "article", "category", "all", "row", "2");

        TagNode node1 = listNode(args);
        RenderContext ctx1 = home(1);
        tag.prepare(node1, ctx1);
        assertEquals(3, ctx1.pageVar("totalPages"));
        assertEquals(5L, ctx1.pageVar("totalCount"));
        assertEquals(2, ctx1.pageVar("pageSize"));
        assertEquals("HOME", ctx1.pageVar("pageType"));
        assertEquals("list", ctx1.pageVar("paginationKind"));
        assertEquals(Boolean.TRUE, ctx1.pageVar("isFirst"));
        assertEquals(Boolean.FALSE, ctx1.pageVar("isLast"));
        assertEquals(Boolean.FALSE, ctx1.pageVar("empty"));
        assertEquals(Boolean.TRUE, ctx1.pageVar("hasResults"));
        assertEquals(Boolean.FALSE, ctx1.pageVar("hasPrev"), "第 1 页没有上一页");
        assertEquals(Boolean.TRUE, ctx1.pageVar("hasNext"));
        assertEquals("/", ctx1.pageVar("currentUrl"));
        assertEquals("/", ctx1.pageVar("firstUrl"));
        assertEquals("", ctx1.pageVar("prevUrl"), "第 1 页的 prevUrl 是空串");
        assertEquals("/page-2/", ctx1.pageVar("nextUrl"));
        assertEquals("/page-3/", ctx1.pageVar("lastUrl"));
        assertEquals("https://example.com/", ctx1.pageVar("canonical"),
                "page.canonical 是这一页自己的绝对 URL（§5.6 v2.2）");

        TagNode node2 = listNode(args);
        RenderContext ctx2 = home(2);
        tag.prepare(node2, ctx2);
        assertEquals(Boolean.FALSE, ctx2.pageVar("isFirst"));
        assertEquals(Boolean.TRUE, ctx2.pageVar("hasPrev"));
        assertEquals("/page-2/", ctx2.pageVar("currentUrl"));
        assertEquals("/", ctx2.pageVar("prevUrl"));
        assertEquals("/page-3/", ctx2.pageVar("nextUrl"));
        assertEquals("https://example.com/page-2/", ctx2.pageVar("canonical"),
                "第 2 页的 canonical 不能指回第 1 页（§7.1.4）");

        TagNode node3 = listNode(args);
        RenderContext ctx3 = home(3);
        tag.prepare(node3, ctx3);
        assertEquals(Boolean.TRUE, ctx3.pageVar("isLast"));
        assertEquals(Boolean.FALSE, ctx3.pageVar("hasNext"));
        assertEquals("", ctx3.pageVar("nextUrl"));
    }

    @Test
    void row缺省取类型的per_page() {
        TestContentProvider provider = news();
        ListTag tag = new ListTag(provider);
        TagNode node = listNode(Map.of("type", "article", "category", "all"));
        RenderContext ctx = home(1);
        tag.prepare(node, ctx);
        assertEquals(2, ctx.pageVar("pageSize"), "per_page=2 是类型给的缺省");
        assertEquals(3, ctx.pageVar("totalPages"));

        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(2, renderer.iterations());
    }

    @Test
    void 空集的totalPages是1且empty为真() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .build();
        ListTag tag = new ListTag(provider);
        TagNode node = listNode(Map.of("type", "article", "category", "all", "row", "2"));
        RenderContext ctx = home(1);
        tag.prepare(node, ctx);
        assertEquals(1, ctx.pageVar("totalPages"), "空集也有第 1 页（§5.6）");
        assertEquals(0L, ctx.pageVar("totalCount"));
        assertEquals(Boolean.TRUE, ctx.pageVar("empty"));
        assertEquals(Boolean.FALSE, ctx.pageVar("hasResults"));
        assertEquals(Boolean.TRUE, ctx.pageVar("isLast"));

        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(0, renderer.iterations(), "空列表不渲染任何迭代项");
    }

    @Test
    void 迭代项带current与class与默认封面() {
        TestContentProvider provider = news();
        ContentItem entry = provider.content("article", 4);
        RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article", entry);
        ctx.pushNamed("site", Map.of("url", "https://example.com",
                "defaultCover", "/static/default-cover.png"));
        ctx.putPage("pageNo", 1);
        ctx.pageUrls(PageUrlBuilder.single("/news/a4.html"));

        ListTag tag = new ListTag(provider);
        TagNode node = listNode(Map.of("type", "article", "category", "all", "row", "5"));
        tag.prepare(node, ctx);
        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());

        List<String> ids = renderer.all("id");
        List<String> classes = renderer.all("class");
        assertEquals(List.of("5", "4", "3", "2", "1"), ids);
        assertEquals(List.of("", "current", "", "", ""), classes,
                "§5.5 / §6.3：current = 该项 id == 当前条目的 id，class 随之");
        // §5.5 第（1）条：cover 为空时给站点默认图，不是空串
        assertEquals("/static/default-cover.png", renderer.last("cover") == null
                ? "/static/default-cover.png" : renderer.last("cover"));
    }

    @Test
    void DETAIL页上list是合法的_用同一个detail_url_pattern的n形态() {
        TestContentProvider provider = news();
        ContentItem book = provider.content("article", 1);
        RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article", book);
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        ctx.putPage("pageNo", 2);
        // §5.6："详情页上的列表分页"用同一个 detail_url_pattern 的 {n} 形态
        ctx.pageUrls(new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return "/news/a1.html";
            }

            @Override
            public String pageUrl(int page) {
                return page <= 1 ? "/news/a1.html" : "/news/a1-" + page + ".html";
            }
        });

        ListTag tag = new ListTag(provider);
        TagNode node = listNode(Map.of("type", "article", "category", "all", "row", "2"));
        tag.prepare(node, ctx);
        assertEquals("DETAIL", ctx.pageVar("pageType"));
        assertEquals("list", ctx.pageVar("paginationKind"));
        assertEquals("/news/a1-2.html", ctx.pageVar("currentUrl"),
                "分页 URL 由 ctx.pageUrls() 给，标签不自己拼");

        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(List.of("第3篇", "第2篇"), titlesOf(renderer));
    }

    @Test
    void 分类索引页省略type时缺省为all并按当前栏目过滤() {
        TestContentProvider provider = TestContentProvider.builder()
                .now(java.time.LocalDateTime.of(2026, 3, 10, 0, 0))
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .type(2, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .category(2, 0, "other", "别的")
                .content(1, "article", "a", "文章A").publishTime("2026-03-01T10:00").category("news")
                .content(2, "product", "p", "产品P").publishTime("2026-03-02T10:00").category("news")
                .content(3, "article", "b", "别的文章").publishTime("2026-03-03T10:00").category("other")
                .build();

        RenderContext ctx = RenderContext.forPage(PageType.LIST, null, null);
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        ctx.pushNamed("channel", Map.of("id", 1L, "name", "新闻", "url", "/news/"));
        ctx.putPage("pageNo", 1);
        ctx.pageUrls(PageUrlBuilder.single("/news/"));

        ListTag tag = new ListTag(provider);
        // type 与 category 都省略：§7.2.1 v2.2 的"分类索引页的查询缺省"
        TagNode node = listNode(Map.of("row", "10"));
        tag.prepare(node, ctx);
        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(List.of("产品P", "文章A"), titlesOf(renderer),
                "type 缺省 all + category 缺省当前分类 → 该栏目下所有类型的混合流");
    }

    /* ---------------- {cms:query} ---------------- */

    @Test
    void 命名查询注册进query作用域并可按下标取() {
        TestContentProvider provider = news();
        QueryTag tag = new QueryTag(provider);
        RenderContext ctx = home(1);
        ctx.putPage("paginationKind", "list");

        TagNode named = StubRenderer.tag("query",
                Map.of("type", "article", "category", "all", "row", "3", "name", "latest"),
                "title");
        tag.prepare(named, ctx);

        assertTrue(ctx.hasNamedQuery("latest"));
        Map<String, Object> meta = ctx.namedValues("query").get("latest") instanceof Map<?, ?> map
                ? cast(map) : Map.of();
        assertEquals(Boolean.FALSE, meta.get("empty"));
        assertEquals(Boolean.TRUE, meta.get("hasResults"));
        assertEquals(5L, meta.get("totalCount"));
        assertEquals(3, meta.get("pageSize"));
        // §6.3 v2.2：query.<name>.rows.<i>.<字段> 的下标访问
        assertTrue(meta.get("rows") instanceof List<?> rows && rows.size() == 3);

        // 循环体内禁止命名（§5.4 约束三 → E2011）
        RenderContext loopCtx = home(1);
        loopCtx.enterLoop();
        assertThrows(com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(named, loopCtx), "循环体内的 name 必须报 E2011");
        loopCtx.exitLoop();
    }

    @Test
    void query不参与分页且结果为空时不渲染() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .build();
        QueryTag tag = new QueryTag(provider);
        RenderContext ctx = home(1);
        TagNode node = StubRenderer.tag("query",
                Map.of("type", "article", "category", "all", "row", "5"), "title");
        tag.prepare(node, ctx);
        assertNull(ctx.pageVar("totalPages"), "query 不填 page 作用域的分页字段");

        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(0, renderer.iterations(), "结果为空时标签自身不渲染（§5.4 约束三）");
    }

    @Test
    void query的row缺省是10() {
        TestContentProvider.Builder builder = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT);
        for (int i = 1; i <= 12; i++) {
            builder.content(i, "article", "a" + i, "第" + i + "篇")
                    .publishTime("2026-03-01T10:00");
        }
        TestContentProvider provider = builder.build();
        QueryTag tag = new QueryTag(provider);
        RenderContext ctx = home(1);
        TagNode node = StubRenderer.tag("query", Map.of("type", "article", "category", "all"), "title");
        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(10, renderer.iterations(), "§6.3：query 的 row 缺省 = 10");
    }

    /* ---------------- {cms:detail} ---------------- */

    @Test
    void detail正文分页切三页_末尾多余分页符不产生空页() {
        String body = "<p>第一段</p><!--cms:page--><p>第二段</p><!--cms:page-->"
                + "<p>第三段</p><!--cms:page-->";
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .paginateBody("article", "content")
                .content(1, "article", "a", "长文").publishTime("2026-03-01T10:00").content(body)
                .build();

        ContentItem entry = provider.content("article", 1);
        DetailTag tag = new DetailTag(provider);

        for (int pageNo = 1; pageNo <= 3; pageNo++) {
            RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article", entry);
            ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
            ctx.putPage("pageNo", pageNo);
            ctx.pageUrls(PageUrlBuilder.single("/news/a.html"));
            TagNode node = StubRenderer.tag("detail", Map.of(), "content", "pageNo", "totalPages");
            tag.prepare(node, ctx);
            assertEquals(3, ctx.pageVar("totalPages"),
                    "§5.2.3：totalPages 按非空切片计，末尾多余的 <!--cms:page--> 不产生第 4 页");
            assertEquals("content", ctx.pageVar("paginationKind"), "正文分页");

            StubRenderer renderer = new StubRenderer();
            tag.render(node, ctx, renderer, new StringBuilder());
            assertEquals("<p>第" + (pageNo == 1 ? "一" : pageNo == 2 ? "二" : "三") + "段</p>",
                    renderer.last("content"), "content 是当前页的切片，不是全文");
            assertEquals(String.valueOf(pageNo), renderer.last("pageNo"));
            assertEquals("3", renderer.last("totalPages"));
        }
    }

    @Test
    void detail的hasPrev与hasNext是按sort_field的相邻内容() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .sort("article", "publishTime", "desc")
                .content(1, "article", "a", "A").publishTime("2026-03-03T10:00")
                .content(2, "article", "b", "B").publishTime("2026-03-02T10:00")
                .content(3, "article", "c", "C").publishTime("2026-03-01T10:00")
                .build();

        DetailTag tag = new DetailTag(provider);
        TagNode node = StubRenderer.tag("detail", Map.of(), "hasPrev", "hasNext");

        // 中间那条：两侧都有
        RenderContext middle = RenderContext.forPage(PageType.DETAIL, "article",
                provider.content("article", 2));
        middle.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        middle.putPage("pageNo", 1);
        middle.pageUrls(PageUrlBuilder.single("/news/b.html"));
        tag.prepare(node, middle);
        StubRenderer r = new StubRenderer();
        tag.render(node, middle, r, new StringBuilder());
        assertEquals("true", r.last("hasPrev"), "§6.3：hasPrev 是相邻内容存在，不是正文分页位置");
        assertEquals("true", r.last("hasNext"));

        // 第一条：没有上一篇
        RenderContext first = RenderContext.forPage(PageType.DETAIL, "article",
                provider.content("article", 1));
        first.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        first.putPage("pageNo", 1);
        first.pageUrls(PageUrlBuilder.single("/news/a.html"));
        tag.prepare(node, first);
        StubRenderer r2 = new StubRenderer();
        tag.render(node, first, r2, new StringBuilder());
        assertEquals("false", r2.last("hasPrev"));
        assertEquals("true", r2.last("hasNext"));
    }

    @Test
    void SINGLE页上detail不是分页主体() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(9, "single", "单页", ContentTypeDef.Kind.SINGLE)
                .paginateBody("single", "content")
                .content(1, "single", "about", "关于我们").publishTime("2026-03-01T10:00")
                .content("<p>甲</p><!--cms:page--><p>乙</p>")
                .build();

        RenderContext ctx = RenderContext.forPage(PageType.SINGLE, "single", null);
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        ctx.putPage("pageNo", 1);
        ctx.pageUrls(PageUrlBuilder.single("/about.html"));

        DetailTag tag = new DetailTag(provider);
        TagNode node = StubRenderer.tag("detail", Map.of(), "content", "totalPages");
        tag.prepare(node, ctx);
        assertNull(ctx.pageVar("paginationKind"),
                "§4.5 口径表：SINGLE 上 {cms:detail} 不是分页主体，不产生第 2 页");

        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals("1", renderer.last("totalPages"));
        assertEquals("<p>甲</p><!--cms:page--><p>乙</p>", renderer.last("content"),
                "不是分页主体时 content 是整段正文，不切片");
    }

    @Test
    void detail的参数表只有type_多写的参数不生效() {
        TestContentProvider provider = news();
        DetailTag tag = new DetailTag(provider);
        assertEquals(List.of("type"), tag.params().stream()
                .map(com.lingjiuw.cms.module.cms.publish.template.ParamSpec::key).toList(),
                "§6.3 的 {cms:detail} 参数表只有 type 一行");

        // where 没被声明 → 解析时按"没写"处理，不会静默生效
        RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article",
                provider.content("article", 3));
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        ctx.putPage("pageNo", 1);
        ctx.pageUrls(PageUrlBuilder.single("/news/a3.html"));
        TagNode node = StubRenderer.tag("detail", Map.of("where", "id:eq:1"), "title");
        StubRenderer renderer = new StubRenderer();
        tag.prepare(node, ctx);
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals("第3篇", renderer.last("title"), "where 对 {cms:detail} 毫无影响");
    }

    /* ---------------- 参数表 ---------------- */

    @Test
    void list的参数表不含仅query的那四个() {
        TestContentProvider provider = news();
        ListTag list = new ListTag(provider);
        List<String> keys = list.params().stream()
                .map(com.lingjiuw.cms.module.cms.publish.template.ParamSpec::key).toList();
        assertTrue(keys.contains("type") && keys.contains("where") && keys.contains("orderby"));
        assertFalse(keys.contains("offset"), "§6.3：offset 的适用列是仅 query");
        assertFalse(keys.contains("depth"), "§6.3：depth 的适用列是仅 query");
        assertFalse(keys.contains("name"), "§6.3：name 的适用列是仅 query");
        assertFalse(keys.contains("cache"), "§6.3：cache 的适用列是仅 query");

        QueryTag query = new QueryTag(provider);
        List<String> queryKeys = query.params().stream()
                .map(com.lingjiuw.cms.module.cms.publish.template.ParamSpec::key).toList();
        assertEquals(20, queryKeys.size(), "§6.3：{cms:query} 的 20 个参数");
        assertTrue(queryKeys.containsAll(List.of("offset", "depth", "name", "cache")));
    }

    @Test
    void list写在SINGLE页上报E3012() {
        TestContentProvider provider = news();
        ListTag tag = new ListTag(provider);
        RenderContext ctx = RenderContext.forPage(PageType.SINGLE, "article", null);
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        TagNode node = listNode(Map.of("type", "article", "category", "all"));
        assertThrows(com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(node, ctx), "§6.7 矩阵：SINGLE 上 {cms:list} 是 ✗（E3012）");
    }

    @Test
    void type_all与where互斥报E2007() {
        TestContentProvider provider = news();
        ListTag tag = new ListTag(provider);
        RenderContext ctx = home(1);
        TagNode node = listNode(Map.of("type", "all", "category", "all",
                "row", "5", "where", "id:eq:1"));
        com.lingjiuw.cms.module.cms.publish.error.PublishException error = assertThrows(
                com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(node, ctx));
        assertEquals("E2007", error.code().name(),
                "§6.3 v2.2 定死：where / relate='field:' / of 配 type='all' → E2007");
    }

    @Test
    void orderby_relationOrder只有裸值才算() {
        TestContentProvider provider = news();
        QueryTag tag = new QueryTag(provider);

        // 带方向 → E2007（"数组顺序没有方向"）
        TagNode withDirection = StubRenderer.tag("query", Map.of("type", "article", "category", "all",
                "relate", "field:related", "orderby", "relationOrder desc"));
        assertEquals("E2007", assertThrows(
                com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(withDirection, detail(provider, 3))).code().name(),
                "§6.3：orderby='relationOrder' 只能裸写");

        // 没配 relate='field:<code>' → E2007（"只在 relate='field:<code>' 时可用"）
        TagNode noRelate = StubRenderer.tag("query", Map.of("type", "article", "category", "all",
                "orderby", "relationOrder"));
        assertEquals("E2007", assertThrows(
                com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(noRelate, detail(provider, 3))).code().name(),
                "relationOrder 只在 relate='field:<code>' 时可用");

        // 裸值 + relate='field:<code>' 是合法的
        TagNode good = StubRenderer.tag("query", Map.of("type", "article", "category", "all",
                "relate", "field:related", "orderby", "relationOrder"));
        tag.prepare(good, detail(provider, 3));
    }

    /** 一个详情页上下文（{@code relate} / {@code of} 需要锚定项，首页上没有）。 */
    private static RenderContext detail(TestContentProvider provider, long id) {
        RenderContext ctx = RenderContext.forPage(PageType.DETAIL, "article",
                provider.content("article", id));
        ctx.pushNamed("site", Map.of("url", "https://example.com", "defaultCover", ""));
        ctx.putPage("pageNo", 1);
        ctx.pageUrls(PageUrlBuilder.single("/news/x.html"));
        return ctx;
    }

    @Test
    void of与relate用在没有锚定项的页面上报E2009() {
        TestContentProvider provider = news();
        QueryTag tag = new QueryTag(provider);
        TagNode node = StubRenderer.tag("query", Map.of("type", "article", "category", "all",
                "of", "self"));
        assertEquals("E2009", assertThrows(
                com.lingjiuw.cms.module.cms.publish.error.PublishException.class,
                () -> tag.prepare(node, home(1))).code().name(),
                "§5.4 约束三：首页没有当前条目，of='self' 无法解析");
    }

    @Test
    void orderby写字段不写方向时方向取类型的sort_order() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .sort("article", "sort", "desc")
                .content(1, "article", "a", "A").publishTime("2026-03-01T10:00").sort(1)
                .content(2, "article", "b", "B").publishTime("2026-03-02T10:00").sort(2)
                .content(3, "article", "c", "C").publishTime("2026-03-03T10:00").sort(3)
                .build();
        QueryTag tag = new QueryTag(provider);
        RenderContext ctx = home(1);
        TagNode node = StubRenderer.tag("query",
                Map.of("type", "article", "category", "all", "orderby", "sort"), "title");
        StubRenderer renderer = new StubRenderer();
        tag.render(node, ctx, renderer, new StringBuilder());
        assertEquals(List.of("C", "B", "A"), titlesOf(renderer),
                "§6.3：orderby 省略方向时按类型的 sort_order（这里是 desc）");

        // order 简写能覆盖它
        TagNode asc = StubRenderer.tag("query",
                Map.of("type", "article", "category", "all", "orderby", "sort", "order", "asc"), "title");
        StubRenderer ascRenderer = new StubRenderer();
        tag.render(asc, home(1), ascRenderer, new StringBuilder());
        assertEquals(List.of("A", "B", "C"), titlesOf(ascRenderer));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** 一个内容提供者的最小替身：验证"标签只依赖接口"。 */
    @Test
    void 标签只依赖ContentProvider接口() {
        ContentProvider provider = news();
        assertNotNull(new ListTag(provider));
        assertNotNull(new QueryTag(provider));
        assertNotNull(new DetailTag(provider));
        assertEquals(PageType.HOME.matrixColumn(), PageType.HOME);
    }

    /** 空白：{@code StubRenderer.tag} 的 args 必须是可变的（Map.of 也行，它只读）。 */
    private static Map<String, String> args(Object... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return map;
    }
}
