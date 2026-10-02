package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.Resolution;
import com.lingjiuw.cms.module.cms.publish.template.tag.ChannelTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.Recorder;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.args;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.bodyTag;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.categoryChannel;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.page;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code {cms:channel}}（static-publish.md §6.4）：五个来源、{@code depth} 的 children、
 * §6.3 的 {@code current} / {@code class} 口径、{@code countScope} 的两种口径、facet 的 {@code urlWith}。
 */
class ChannelTagTest {

    /** 分类树：新闻(1) → 技术(2) / 生活(3)；另有顶级分类"关于"(4)；前两个分类下各一条内容。 */
    private static TestContentProvider categoryTree() {
        return TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .category(2, 1, "tech", "技术")
                .category(3, 1, "life", "生活")
                .category(4, 0, "about", "关于")
                .content(ContentSpec.of(11, "article", "a", "文章A")
                        .publishTime("2026-05-01T10:00").category("news"))
                .content(ContentSpec.of(12, "article", "b", "文章B")
                        .publishTime("2026-05-02T10:00").category("tech"))
                .build();
    }

    @Test
    void 分类树depth为2时子项在children里() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        RenderContext ctx = page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news"));

        tag.render(bodyTag("channel", args("source", "category", "depth", "2")), ctx, recorder,
                new StringBuilder());

        assertEquals(2, recorder.size());
        // depth>1 时**叶子也要有 children（空列表）**：§6.4 的官方示例是
        // {cms:if field='children'} / {cms:foreach field='children'}，而字段解析找不到就报错（§5.1）。
        // 缺这个 key 会让每一页的导航在叶子节点上渲染失败——"产出字段恒在"是标签的契约。
        assertTrue(recorder.frame(1).containsKey("children"));
        assertEquals(List.of(), recorder.frame(1).get("children"));
        Map<String, Object> news = recorder.frame(0);
        assertEquals("新闻", news.get("label"));
        assertEquals("/news/", news.get("url"));
        assertEquals(true, news.get("current"));
        assertEquals("active", news.get("class"));
        assertEquals(2L, news.get("count"));

        List<NavItem> children = children(news);
        assertEquals(List.of("技术", "生活"), children.stream().map(NavItem::label).toList());
        assertEquals(List.of("/tech/", "/life/"), children.stream().map(NavItem::url).toList());
        // 子项自己也算过 current / class（深度不限层级）
        assertEquals("", children.get(0).get("class"));
        assertEquals(false, children.get(0).get("current"));
    }

    @Test
    void children能被foreach迭代出字段() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("depth", "2")),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), recorder,
                new StringBuilder());

        // {cms:foreach field='children'} 压栈的就是子项的 values()，这里按同一路径取一次
        RenderContext probe = page(PageType.LIST, "article", null, null);
        probe.pushAnonymous(children(recorder.frame(0)).get(0).values());

        Resolution url = probe.resolve(List.of("url"));
        assertTrue(url.found());
        assertEquals("/tech/", url.value());
        assertEquals("技术", probe.resolve(List.of("label")).value());
    }

    @Test
    void depth为1时每项不带children() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), recorder,
                new StringBuilder());

        assertFalse(recorder.frame(0).containsKey("children"));
    }

    @Test
    void 当前分类自身是active无关项是空串() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        // 当前栏目 = 新闻(1)：它自身 active，"关于"(4) 与它无关
        tag.render(bodyTag("channel", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), recorder,
                new StringBuilder());

        assertEquals("active", recorder.frame(0).get("class"));
        assertEquals("", recorder.frame(1).get("class"));
        assertEquals(false, recorder.frame(1).get("current"));
    }

    @Test
    void 当前分类的祖先是activeTrail() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        // 当前栏目 = 技术(2)：新闻(1) 是它的祖先 → active-trail
        tag.render(bodyTag("channel", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(2, "技术", "tech")), recorder,
                new StringBuilder());

        assertEquals("active-trail", recorder.frame(0).get("class"));
        assertEquals(true, recorder.frame(0).get("current"));
        assertEquals("", recorder.frame(1).get("class"));

        // 从父分类往下看时，技术(2) 是自身 → active
        Recorder nested = new Recorder();
        tag.render(bodyTag("channel", args("channel", "news")),
                page(PageType.LIST, "article", null, categoryChannel(2, "技术", "tech")), nested,
                new StringBuilder());
        assertEquals("active", nested.frame(0).get("class"));
        assertEquals("", nested.frame(1).get("class"));
    }

    @Test
    void countScope的两种口径数值不同() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder tree = new Recorder();
        tag.render(bodyTag("channel", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), tree,
                new StringBuilder());
        Recorder node = new Recorder();
        tag.render(bodyTag("channel", args("countScope", "node")),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), node,
                new StringBuilder());

        // 新闻自己 1 条，子分类技术 1 条：tree = 2，node = 1
        assertEquals(2L, tree.frame(0).get("count"));
        assertEquals(1L, node.frame(0).get("count"));
    }

    @Test
    void 起始节点从该分类的下一层展开() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("channel", "news")),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), recorder,
                new StringBuilder());

        assertEquals(List.of("技术", "生活"), recorder.frames().stream().map(f -> f.get("label")).toList());
    }

    @Test
    void 起始节点不存在报E2006() {
        ChannelTag tag = new ChannelTag(categoryTree());
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("channel", args("channel", "nope")), page(PageType.LIST, "article"),
                new Recorder(), new StringBuilder()));

        assertEquals(PublishErrorCode.E2006, error.code());
        assertTrue(error.getMessage().contains("分类 nope 不存在"), error.getMessage());
    }

    @Test
    void 菜单里kind为url的项按目标URL判定当前() {
        ChannelTag tag = new ChannelTag(menuProvider());
        Recorder recorder = new Recorder();
        RenderContext ctx = page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news"));
        ctx.putPage("url", "/rank/price/");

        tag.render(bodyTag("channel", args("source", "menu", "code", "main")), ctx, recorder,
                new StringBuilder());

        assertEquals(4, recorder.size());
        assertEquals("active", recorder.frame(0).get("class"));
        assertEquals(true, recorder.frame(0).get("current"));
        assertEquals("", recorder.frame(1).get("class"));
        // kind='category' 指向新闻(1)，当前栏目就是它 → active
        assertEquals("active", recorder.frame(2).get("class"));
        // kind='custom' 不指向任何东西 → 永远不高亮
        assertEquals("", recorder.frame(3).get("class"));
    }

    @Test
    void 菜单depth为2时带上children() {
        ChannelTag tag = new ChannelTag(menuProvider());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("source", "menu", "code", "main", "depth", "2")),
                page(PageType.LIST, "article"), recorder, new StringBuilder());

        List<NavItem> children = children(recorder.frame(0));
        assertEquals(1, children.size());
        assertEquals("价格榜 · 月度", children.get(0).label());
        assertEquals("/rank/price/month/", children.get(0).url());
    }

    @Test
    void 菜单不存在报E2006() {
        ChannelTag tag = new ChannelTag(menuProvider());
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("channel", args("source", "menu", "code", "nope")),
                page(PageType.LIST, "article"), new Recorder(), new StringBuilder()));

        assertEquals(PublishErrorCode.E2006, error.code());
        assertTrue(error.getMessage().contains("本站菜单有 main"), error.getMessage());
    }

    @Test
    void source为type时按当前页面类型给current() {
        ChannelTag tag = new ChannelTag(TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .type(2, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .build());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("source", "type")), page(PageType.LIST, "product"), recorder,
                new StringBuilder());

        assertEquals(2, recorder.size());
        assertEquals("", recorder.frame(0).get("class"));
        assertEquals("current", recorder.frame(1).get("class"));
        assertEquals(true, recorder.frame(1).get("current"));
    }

    @Test
    void source为type时row取前N个() {
        ChannelTag tag = new ChannelTag(TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .type(2, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .build());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("source", "type", "row", "1")), page(PageType.LIST, "product"),
                recorder, new StringBuilder());

        assertEquals(1, recorder.size());
        assertEquals("文章", recorder.frame(0).get("label"));
    }

    @Test
    void source为content时按层级组成children() {
        ChannelTag tag = new ChannelTag(docProvider());
        Recorder depth1 = new Recorder();
        tag.render(bodyTag("channel", args("source", "content", "type", "doc", "depth", "1")),
                page(PageType.LIST, "doc"), depth1, new StringBuilder());
        Recorder depth2 = new Recorder();
        tag.render(bodyTag("channel", args("source", "content", "type", "doc", "depth", "2")),
                page(PageType.LIST, "doc"), depth2, new StringBuilder());

        assertEquals(1, depth1.size());
        assertFalse(depth1.frame(0).containsKey("children"));
        assertEquals(1, depth2.size());
        List<NavItem> children = children(depth2.frame(0));
        assertEquals(1, children.size());
        assertEquals("第二篇", children.get(0).label());
        assertEquals("/doc/two.html", children.get(0).url());
    }

    @Test
    void source为content时当前条目与它的祖先是active与activeTrail() {
        TestContentProvider provider = docProvider();
        ChannelTag tag = new ChannelTag(provider);
        Recorder recorder = new Recorder();
        ContentItem entry = provider.contentById(2);
        tag.render(bodyTag("channel", args("source", "content", "type", "doc", "depth", "2")),
                page(PageType.DETAIL, "doc", entry, categoryChannel(1, "新闻", "news")), recorder,
                new StringBuilder());

        assertEquals("active-trail", recorder.frame(0).get("class"));
        assertEquals("active", children(recorder.frame(0)).get(0).get("class"));
    }

    @Test
    void facet取值条的current与urlWith() {
        ChannelTag tag = new ChannelTag(facetProvider());
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("label", "Acme");
        channel.put("slug", "brand-acme");
        channel.put("url", "/f/brand-acme/");
        channel.put("facetPath", "brand-acme");
        channel.put("count", 1);
        Recorder brands = new Recorder();
        tag.render(bodyTag("channel", args("source", "facet", "field", "brand")),
                page(PageType.FACET, "product", null, channel), brands, new StringBuilder());

        assertEquals(2, brands.size());
        assertEquals("current", brands.frame(0).get("class"));
        // 当前组合就是 brand-acme，不在 facets.combos 里 → 空串
        assertEquals("", brands.frame(0).get("urlWith"));
        assertEquals("", brands.frame(1).get("class"));
        assertEquals("", brands.frame(1).get("urlWith"));

        Recorder regions = new Recorder();
        tag.render(bodyTag("channel", args("source", "facet", "field", "region")),
                page(PageType.FACET, "product", null, channel), regions, new StringBuilder());

        assertEquals(1, regions.size());
        // brand-acme + region-huadong 在 facets.combos 里声明过 → 输出叠加链接
        assertEquals("/f/brand-acme+region-huadong/", regions.frame(0).get("urlWith"));
    }

    @Test
    void facet缺field参数时报E1002并列出参数() {
        ChannelTag tag = new ChannelTag(facetProvider());
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("channel", args("source", "facet")), page(PageType.FACET, "product"),
                new Recorder(), new StringBuilder()));

        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("field"), error.getMessage());
    }

    @Test
    void 首页上没有浏览位置时不高亮也不报错() {
        ChannelTag tag = new ChannelTag(categoryTree());
        Recorder recorder = new Recorder();
        tag.render(bodyTag("channel", args("source", "category")), page(PageType.HOME, null), recorder,
                new StringBuilder());

        assertEquals(2, recorder.size());
        assertEquals(List.of("", ""), recorder.frames().stream().map(f -> f.get("class")).toList());
        assertEquals(List.of(false, false), recorder.frames().stream().map(f -> f.get("current")).toList());
    }

    @Test
    void maxCount除feed外一律合法() {
        ChannelTag tag = new ChannelTag(categoryTree());
        for (PageType pageType : PageType.values()) {
            assertEquals(pageType == PageType.FEED ? 0 : -1, tag.maxCount(pageType), pageType.name());
        }
    }

    @Test
    void feed上报E3012() {
        ChannelTag tag = new ChannelTag(categoryTree());
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("channel", Map.of()), page(PageType.FEED, null), new Recorder(),
                new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, error.code());
    }

    /* ---------------- 夹具 ---------------- */

    private static TestContentProvider docProvider() {
        return TestContentProvider.builder()
                .type(1, "doc", "文档", ContentTypeDef.Kind.TREE)
                .content(ContentSpec.of(1, "doc", "one", "第一篇").publishTime("2026-05-01T10:00"))
                .content(ContentSpec.of(2, "doc", "two", "第二篇").parent(1).publishTime("2026-05-02T10:00"))
                .build();
    }

    private static TestContentProvider facetProvider() {
        return TestContentProvider.builder()
                .type(1, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .enumField("product", "brand", "acme:Acme", "beta:Beta")
                .enumField("product", "region", "huadong:华东")
                .content(ContentSpec.of(1, "product", "p1", "产品1").field("brand", "acme")
                        .field("region", "huadong"))
                .content(ContentSpec.of(2, "product", "p2", "产品2").field("brand", "beta"))
                .siteOption("facets.combos", List.of("brand-acme+region-huadong"))
                .build();
    }

    private static TestContentProvider menuProvider() {
        List<NavItem> items = new ArrayList<>();
        Map<String, Object> price = menuItem(1, "url", 0, null, "/rank/price/", "价格榜");
        price.put("children", List.of(NavItem.of(menuItem(5, "url", 0, null, "/rank/price/month/",
                "价格榜 · 月度"))));
        items.add(NavItem.of(price));
        items.add(NavItem.of(menuItem(2, "url", 0, null, "/rank/new/", "最新榜")));
        items.add(NavItem.of(menuItem(3, "category", 1, "news", null, "新闻")));
        items.add(NavItem.of(menuItem(4, "custom", 0, null, null, "更多")));
        return TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .menu("main", items)
                .build();
    }

    private static Map<String, Object> menuItem(long id, String kind, long refId, String refCode,
                                                String url, String label) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("label", label);
        values.put("kind", kind);
        values.put("refId", refId);
        values.put("refCode", refCode);
        values.put("url", url);
        values.put("target", "");
        values.put("rel", "");
        return values;
    }

    private static List<NavItem> children(Map<String, Object> frame) {
        Object value = frame.get("children");
        assertTrue(value instanceof List<?>, "children 应当是列表，实际是 " + value);
        List<NavItem> items = new ArrayList<>();
        for (Object element : (List<?>) value) {
            items.add((NavItem) element);
        }
        return items;
    }
}
