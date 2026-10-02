package com.lingjiuw.cms.module.cms.publish.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.entity.CmsMenu;
import com.lingjiuw.cms.module.cms.entity.CmsMenuItem;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuItemMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSitePublishOptionMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbContentProvider} 的真库验收（PostgreSQL）：在事务里插一小份 fixture，断言完回滚。
 *
 * <p><b>为什么要真库</b>：这一层实现的是 SQL——时间窗、栏目子树、层级 CTE、邻接的窗口函数、
 * 索引表筛选，没有内存替身能证明它们对。因此本类**不造假 DB**，而是：
 * <ul>
 *   <li>{@code @SpringBootTest}（不起 Web 容器）+ {@code @Transactional}：测试结束回滚，不动开发库的数据；</li>
 *   <li>{@code spring.flyway.enabled=false}：不因为别的代理正在改迁移文件而报校验和错；</li>
 *   <li><b>每个用例插一个独立站点</b>：站点隔离本来就是数据层的硬要求，用例因此不会被库里
 *       已有内容干扰（也就不需要"断言前先清库"这种脆弱做法）；</li>
 *   <li>{@link EnabledIf} 守门：库连不上时整类跳过（不误报失败），报告里如实写"没跑"。</li>
 * </ul>
 */
@EnabledIf("databaseReachable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "logging.level.root=warn"})
@Transactional
class DbContentProviderTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/lingjiuw_cms";
    private static final String USER = "cms";
    private static final String PASSWORD = "cms123456";

    /** 数据库连不上就整类跳过（application.yml 指向的就是这个库）。 */
    static boolean databaseReachable() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private DbContentProviderFactory factory;
    @Autowired
    private CmsSiteMapper siteMapper;
    @Autowired
    private CmsSitePublishOptionMapper optionMapper;
    @Autowired
    private CmsContentMapper contentMapper;
    @Autowired
    private CmsContentTypeMapper contentTypeMapper;
    @Autowired
    private CmsFieldMapper fieldMapper;
    @Autowired
    private CmsContentIndexMapper contentIndexMapper;
    @Autowired
    private CmsCategoryMapper categoryMapper;
    @Autowired
    private CmsTagMapper tagMapper;
    @Autowired
    private CmsContentCategoryMapper contentCategoryMapper;
    @Autowired
    private CmsContentTagMapper contentTagMapper;
    @Autowired
    private CmsMenuMapper menuMapper;
    @Autowired
    private CmsMenuItemMapper menuItemMapper;

    private long siteId;
    private ContentProvider provider;

    @BeforeEach
    void setUp() {
        CmsSite site = new CmsSite();
        site.setName("静态发布测试站");
        site.setCode("publish-test-" + UUID.randomUUID());
        site.setRootDir("publish-test");
        site.setStatus(1);
        site.setIsDefault(0);
        siteMapper.insert(site);
        siteId = site.getId();
        provider = factory.forSite(siteId);
    }

    /* ---------------- 定义与站点配置 ---------------- */

    @Test
    void 类型定义与字段定义从库里读出来() {
        insertType("product", "产品", "CONTENT", 0, 10);
        insertType("single", "单页", "SINGLE", 0, 20);
        insertField("product", "brand", "品牌", "TEXT", 0, 1, 0);

        assertEquals(List.of("product", "single"), provider.types().stream()
                .map(ContentTypeDef::code).toList(), "类型按 sort, id 排（§2.1）");

        ContentTypeDef product = provider.type("product");
        assertEquals(ContentTypeDef.Kind.CONTENT, product.kind());
        assertEquals(1, product.fields().size());
        assertEquals("brand", product.fields().get(0).code());
        assertEquals("品牌", product.fields().get(0).label());
        assertTrue(product.fields().get(0).indexed());
        assertNull(provider.type("nope"));
        // cms_form 还不存在（期 3）：返回"没有"而不是抛异常
        assertNull(provider.form("contact"));
        assertTrue(provider.formCodes().isEmpty());
    }

    @Test
    void 站点选项按类型解析且空串视为未设置() {
        insertOption("page.archive", "0");
        insertOption("page.tag", "1");
        insertOption("publish.keepReleases", "3");
        insertOption("url.tag", "/tag/{tagSlug}/");
        insertOption("feed.types", "[\"article\"]");
        insertOption("media.host", "");

        SiteConfig site = provider.site();
        assertEquals(siteId, site.id());
        // 值必须解析成 Boolean，flag() 才拿得到 false（字符串 "0" 也能判，但 List / 数字不行）
        assertFalse(site.flag("page.archive", true));
        assertTrue(site.flag("page.tag", false));
        assertEquals(3, site.number("publish.keepReleases", 0));
        assertEquals("/tag/{tagSlug}/", site.option("url.tag", "x"));
        assertTrue(site.options().get("feed.types") instanceof List<?>);
        // 空串 = 未设置：不进选项表，mediaHost 取空串
        assertEquals("", site.mediaHost());
        assertFalse(site.options().containsKey("media.host"));
    }

    /* ---------------- 列表、计数与时间窗 ---------------- */

    @Test
    void 列表与计数同口径且时间窗生效() {
        insertType("article", "文章", "CONTENT", 0, 10);
        insertOption("url.list", "/{categoryPath}/page-{n}/");
        long news = insertCategory(0L, "news", "新闻");
        long tech = insertCategory(news, "tech", "技术");
        LocalDateTime now = LocalDateTime.now();
        long published = insertContent("article", "published-a", "已发布", now.minusDays(1), "PUBLISHED");
        insertCategoryLink(published, tech, "primary");
        insertContent("article", "future", "还没到点", now.plusDays(2), "PUBLISHED");
        long expired = insertContent("article", "expired", "已到期", now.minusDays(3), "PUBLISHED");
        setExpireTime(expired, now.minusDays(1));
        insertContent("article", "draft", "草稿", now.minusDays(1), "DRAFT");

        ContentQuery query = new ContentQuery().type("article").category("all");
        ContentProvider.QueryResult result = provider.query(query);
        assertEquals(1L, result.totalCount(), "只有一条现在可见（定时未到点 / 到期 / 草稿都不算）");
        assertEquals(List.of("已发布"), titles(result.rows()));
        assertEquals(result.totalCount(), provider.count(query), "计数与列表必须同口径");

        // status='any'：草稿与未到点的都看得见，**到期的仍然退出**（到期窗口与状态无关，§6.3 v2.2）
        ContentQuery any = new ContentQuery().type("article").category("all").status("any")
                .orderby(List.of(new OrderBy("id", true)));
        assertEquals(List.of("草稿", "还没到点", "已发布"), titles(provider.query(any).rows()));

        // 栏目子树：category='news' 含子分类 tech（includeChildren 默认 true）
        ContentQuery withChildren = new ContentQuery().type("article").category("news");
        assertEquals(1L, provider.count(withChildren));
        assertEquals(0L, provider.count(withChildren.copy().includeChildren(false)));

        // 分页切片 + 总数与页无关（row=0 取全部）
        ContentQuery paged = new ContentQuery().type("article").category("all").row(1);
        assertEquals(1, provider.query(paged).rows().size());
        assertEquals(1L, provider.query(paged).totalCount());
        assertEquals(0, provider.query(new ContentQuery().type("article").category("all")).pageSize(),
                "row=0 表示不分页");
    }

    @Test
    void 派生字段_URL_分类_标签_作者() {
        insertType("article", "文章", "CONTENT", 0, 10);
        insertType("author", "作者", "CONTENT", 0, 20);
        insertOption("url.list", "/{categoryPath}/page-{n}/");
        insertOption("url.tag", "/tag/{tagSlug}/");
        long news = insertCategory(0L, "news", "新闻");
        long tech = insertCategory(news, "tech", "技术");
        long hot = insertTag("hot", "热点");
        long authorId = insertContent("author", "zhangsan", "张三", LocalDateTime.now(), "PUBLISHED");
        LocalDateTime now = LocalDateTime.now();
        long id = insertContent("article", "derived", "派生字段", now.minusHours(2), "PUBLISHED");
        setAuthor(id, authorId, "张三");
        insertCategoryLink(id, tech, "primary");
        insertTagLink(id, hot);

        ContentItem item = provider.contentBySlug("article", "derived");
        assertNotNull(item);
        assertEquals("/news/tech/derived.html", item.get("url"), "{categoryPath}/{slug} 的详情 URL");
        assertEquals("/news/tech", item.get("categoryPath"));
        assertEquals("技术", item.get("categoryName"));
        assertEquals("/news/tech/", item.get("categoryUrl"),
                "分类索引 URL 是该分类的 url.list 第 1 页形态（主分类是 tech）");
        assertEquals("article", item.get("typeCode"));
        assertEquals("文章", item.get("typeName"));
        assertEquals("张三", item.get("authorName"));
        assertEquals("/author/zhangsan.html", item.get("authorUrl"), "作者是 type_code='author' 的内容项");
        assertTrue(String.valueOf(item.get("canonical")).endsWith("/news/tech/derived.html"));
        assertEquals(4, item.get("wordCount"), "正文字段没填 word_count 时按纯文本兜底");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) item.get("tags");
        assertEquals(1, tags.size());
        assertEquals("hot", tags.get(0).get("slug"));
        assertEquals("/tag/hot/", tags.get(0).get("url"));
        assertEquals(1L, tags.get(0).get("count"));

        // 标签 / 分类的计数与列表同一个时间窗（§6.3）
        assertEquals(1L, provider.category("tech").get("count"));
        assertEquals(1L, provider.tag("hot").get("count"));
        assertTrue(provider.categoryExists("news"));
        assertTrue(provider.categoryExists(String.valueOf(news)), "id 与 slug 都认");
        assertTrue(provider.tagExists("hot"));
        assertTrue(provider.authorExists("zhangsan"), "作者按 slug 认");
        assertTrue(provider.authorExists(String.valueOf(authorId)), "作者按 id 认");
        assertNull(provider.category("nope"));
    }

    @Test
    void 导航树与归档() {
        insertType("article", "文章", "CONTENT", 0, 10);
        insertOption("url.list", "/{categoryPath}/page-{n}/");
        long news = insertCategory(0L, "news", "新闻");
        long tech = insertCategory(news, "tech", "技术");
        LocalDateTime now = LocalDateTime.now();
        long id = insertContent("article", "nav", "导航", now.minusDays(1), "PUBLISHED");
        insertCategoryLink(id, tech, "primary");

        List<NavItem> top = provider.categories(null, 2, ContentProvider.CountScope.tree);
        NavItem newsNode = top.stream().filter(item -> "news".equals(item.get("slug")))
                .findFirst().orElseThrow();
        assertEquals(1L, newsNode.get("count"), "countScope=tree 含子分类");
        assertEquals("/news/", newsNode.get("url"), "url.list 的第 1 页形态：{n} 与相邻分隔符一起删");
        assertEquals("/news", newsNode.get("path"));
        assertEquals(1, newsNode.children().size());
        assertEquals("/news/tech", newsNode.children().get(0).get("path"));

        List<NavItem> nodeOnly = provider.categories(0L, 1, ContentProvider.CountScope.node);
        assertEquals(0L, nodeOnly.stream().filter(item -> "news".equals(item.get("slug")))
                .findFirst().orElseThrow().get("count"), "countScope=node 只算本节点");

        assertEquals(List.of("news", "tech"), provider.categoryAncestors(tech).stream()
                .map(item -> item.get("slug")).toList(), "从顶到下、含自身");

        List<NavItem> months = provider.archives("article", "month", 0, "all", true);
        assertEquals(1, months.size());
        assertEquals(1L, months.get(0).get("count"));
        assertEquals(now.getYear(), months.get(0).get("year"));
        assertEquals(now.getMonthValue(), months.get(0).get("month"));
        String monthUrl = String.valueOf(months.get(0).get("url"));
        assertTrue(monthUrl.startsWith("/archive/" + now.getYear() + "/"), monthUrl);
    }

    @Test
    void 层级内容的children与祖先链() {
        insertType("book", "书", "TREE", 1, 10);
        insertType("chapter", "章", "TREE", 1, 20);
        LocalDateTime now = LocalDateTime.now();
        long book = insertContent("book", "book-a", "书A", now.minusDays(5), "PUBLISHED");
        long chapter1 = insertContent("chapter", "ch-1", "第一章", now.minusDays(4), "PUBLISHED");
        setParent(chapter1, book);
        long chapter2 = insertContent("chapter", "ch-2", "第二章", now.minusDays(3), "PUBLISHED");
        setParent(chapter2, book);
        long section = insertContent("chapter", "s-1", "第一节", now.minusDays(2), "PUBLISHED");
        setParent(section, chapter1);

        assertEquals(List.of("第一章", "第二章"), titles(provider.children(book, "chapter", 1)));
        assertEquals(List.of("第一章", "第二章", "第一节"), titles(provider.children(book, "chapter", 2)),
                "depth>1 是到 depth 层为止的全部后代，扁平，按 sort asc, id asc");
        assertEquals(List.of("书A", "第一章"), titles(provider.contentAncestors(section)), "从顶到下");

        ContentItem parent = provider.contentById(book);
        assertEquals(2L, parent.get("childCount"));
        assertEquals(Boolean.TRUE, parent.get("hasChildren"));
        assertEquals("书A", provider.contentById(chapter1).get("parentTitle"));
        assertEquals("/book/book-a.html", provider.contentById(chapter1).get("parentUrl"),
                "父内容没有 pattern 时兜底 /{typeCode}/{slug}.html");

        // depth>1 的查询给每项预加载 children（§6.3）
        ContentQuery query = new ContentQuery().type("chapter").category("all").depth(2);
        ContentItem first = provider.query(query).rows().stream()
                .filter(item -> item.id() == chapter1).findFirst().orElseThrow();
        assertNotNull(first.get("children"));
    }

    @Test
    void 邻接方向与排序口径() {
        insertType("book", "书", "TREE", 1, 10);
        LocalDateTime now = LocalDateTime.now();
        long first = insertContent("book", "b-1", "第一篇", now.minusDays(1), "PUBLISHED");
        long second = insertContent("book", "b-2", "第二篇", now.minusDays(2), "PUBLISHED");
        long third = insertContent("book", "b-3", "第三篇", now.minusDays(3), "PUBLISHED");

        // book 没配 sort_field ⇒ 默认 publishTime desc：第一篇 → 第二篇 → 第三篇
        ContentProvider.Neighbors middle = provider.neighbors("book", second, "type", "all");
        assertNotNull(middle.prev());
        assertEquals("第一篇", middle.prev().title(), "序列里靠前的那条是上一篇（§6.4）");
        assertNotNull(middle.next());
        assertEquals("第三篇", middle.next().title());

        assertNull(provider.neighbors("book", first, "type", "all").prev(), "第一条没有上一篇");
        assertNull(provider.neighbors("book", third, "type", "all").next(), "最后一条没有下一篇");
        assertEquals(ContentProvider.Neighbors.NONE, provider.neighbors("book", 987654321L, "type", "all"));
    }

    @Test
    void 类型节点与facet取值() {
        insertType("product", "产品", "CONTENT", 0, 10);
        insertTypeField("product", "region", "地区", "ENUM", 1, "huadong:华东");
        insertOption("url.list", "/{categoryPath}/page-{n}/");
        long category = insertCategory(0L, "products", "产品栏目");
        long content = insertContent("product", "p1", "产品一", LocalDateTime.now().minusDays(1),
                "PUBLISHED");
        insertCategoryLink(content, category, "primary");
        insertIndexRow(content, "product", "region", "huadong", "ENUM");

        // {cms:channel source='type'} 的取数：一个类型一个导航项，count 与列表同口径（§6.4）
        NavItem node = provider.typeNodes().stream()
                .filter(item -> "product".equals(item.get("typeCode"))).findFirst().orElseThrow();
        assertEquals(1L, node.get("count"));
        assertEquals("产品", node.get("label"));
        assertEquals("product", node.get("slug"), "slug = 类型 code");
        assertEquals("/product/", node.get("url"), "列表页 URL 的第 1 页形态");

        // facet 取值条（§7.4）：label 来自字段定义的 options，url 来自站点选项 url.facet 的默认值
        List<NavItem> values = provider.facetValues("product", "region");
        assertEquals(1, values.size());
        assertEquals("huadong", values.get(0).get("slug"));
        assertEquals("华东", values.get(0).get("label"));
        assertEquals(1L, values.get(0).get("count"));
        assertEquals("region-huadong", values.get(0).get("facetPath"));
        assertEquals("/f/region-huadong/", values.get(0).get("url"));
    }

    @Test
    void 菜单按kind现算URL() {
        insertType("article", "文章", "CONTENT", 0, 10);
        long content = insertContent("article", "about", "关于我们", LocalDateTime.now(), "PUBLISHED");
        long menuId = insertMenu("main", "主导航");
        insertMenuItem(menuId, "content", content, null, null, 1);
        insertMenuItem(menuId, "url", null, "/external/", "外部", 2);

        assertTrue(provider.menuCodes().contains("main"));
        List<NavItem> items = provider.menu("main");
        NavItem contentItem = items.stream().filter(item -> "content".equals(item.get("kind")))
                .findFirst().orElseThrow();
        assertEquals("/about.html", contentItem.get("url"), "详情 URL 按类型的 pattern 现算");
        assertEquals("关于我们", contentItem.get("label"), "label 为空时取所指向对象的名称");
        NavItem urlItem = items.stream().filter(item -> "url".equals(item.get("kind")))
                .findFirst().orElseThrow();
        assertEquals("/external/", urlItem.get("url"), "kind='url' 的地址原样透传");
        assertTrue(provider.menu("nope").isEmpty());
    }

    @Test
    void 没有数据时也不抛异常() {
        assertFalse(provider.categoryExists("nope"));
        assertFalse(provider.tagExists("nope"));
        assertFalse(provider.authorExists("nope"));
        assertTrue(provider.facetValues("article", "region").isEmpty());
        assertTrue(provider.tags("nope", 10, "count", 1).isEmpty());
        assertTrue(provider.children(987654321L, "article", 1).isEmpty());
        assertTrue(provider.contentAncestors(987654321L).isEmpty());
        assertNull(provider.contentById(987654321L));
        assertNull(provider.content("article", 987654321L));
        assertEquals(siteId, provider.siteId());
        assertEquals(siteId, provider.site().id());
        assertNotNull(provider.site());
        // 没有类型定义 / 没有内容：空结果，不是异常
        ContentProvider.QueryResult empty = provider.query(new ContentQuery().type("all").category("all"));
        assertEquals(0L, empty.totalCount());
        assertTrue(empty.rows().isEmpty());
        assertTrue(provider.types().stream().noneMatch(def -> "article".equals(def.code())));
        assertTrue(provider.categories(null, 1, ContentProvider.CountScope.tree).isEmpty());
    }

    /* ---------------- fixture ---------------- */

    private void insertType(String code, String name, String kind, int hierarchical, int sort) {
        CmsContentType row = new CmsContentType();
        row.setSiteId(siteId);
        row.setCode(code);
        row.setName(name);
        row.setKind(kind);
        row.setHierarchical(hierarchical);
        row.setPerPage(20);
        row.setStatus(1);
        row.setSort(sort);
        if ("CONTENT".equals(kind) && code.equals("article")) {
            row.setDetailUrlPattern("/{categoryPath}/{slug}.html");
            row.setListUrlPattern("/{typeCode}/page-{n}/");
        } else if (code.equals("author")) {
            row.setDetailUrlPattern("/author/{slug}.html");
        }
        contentTypeMapper.insert(row);
    }

    private void insertField(String typeCode, String code, String label, String fieldType,
                             int raw, int indexed, int sort) {
        insertTypeField(typeCode, code, label, fieldType, indexed, null);
    }

    private void insertTypeField(String typeCode, String code, String label, String fieldType,
                                 int indexed, String options) {
        CmsField row = new CmsField();
        row.setSiteId(siteId);
        row.setTypeCode(typeCode);
        row.setCode(code);
        row.setLabel(label);
        row.setFieldType(fieldType);
        row.setRaw(0);
        row.setIndexed(indexed);
        row.setSearchable(0);
        row.setRequired(0);
        row.setCrossSite(0);
        row.setOptions(options);
        row.setSort(0);
        fieldMapper.insert(row);
    }

    private void insertIndexRow(long contentId, String typeCode, String fieldCode, String valueKey,
                                String valueType) {
        CmsContentIndex row = new CmsContentIndex();
        row.setSiteId(siteId);
        row.setContentId(contentId);
        row.setTypeCode(typeCode);
        row.setFieldCode(fieldCode);
        row.setValueKey(valueKey);
        row.setValueType(valueType);
        row.setStrValue(valueKey);
        contentIndexMapper.insert(row);
    }

    private void insertOption(String code, String value) {
        CmsSitePublishOption row = new CmsSitePublishOption();
        row.setSiteId(siteId);
        row.setOptionCode(code);
        row.setValue(value);
        optionMapper.insert(row);
    }

    private long insertCategory(long parentId, String slug, String name) {
        CmsCategory row = new CmsCategory();
        row.setSiteId(siteId);
        row.setParentId(parentId);
        row.setSlug(slug);
        row.setName(name);
        row.setSort(0);
        row.setStatus(1);
        categoryMapper.insert(row);
        return row.getId();
    }

    private long insertTag(String slug, String name) {
        CmsTag row = new CmsTag();
        row.setSiteId(siteId);
        row.setSlug(slug);
        row.setName(name);
        tagMapper.insert(row);
        return row.getId();
    }

    private long insertContent(String typeCode, String slug, String title, LocalDateTime publishTime,
                               String status) {
        CmsContent row = new CmsContent();
        row.setSiteId(siteId);
        row.setTypeCode(typeCode);
        row.setParentId(0L);
        row.setSlug(slug);
        row.setTitle(title);
        row.setStatus(status);
        row.setSort(0);
        row.setTop(0);
        row.setRecommend(0);
        row.setViewCount(0L);
        row.setContentFormat("RICHTEXT");
        row.setContent("<p>正文内容</p>");
        row.setContentHtml("<p>正文内容</p>");
        row.setPublishTime(publishTime);
        contentMapper.insert(row);
        return row.getId();
    }

    private void setExpireTime(long contentId, LocalDateTime expireTime) {
        CmsContent update = new CmsContent();
        update.setId(contentId);
        update.setExpireTime(expireTime);
        contentMapper.updateById(update);
    }

    private void setParent(long contentId, long parentId) {
        CmsContent update = new CmsContent();
        update.setId(contentId);
        update.setParentId(parentId);
        contentMapper.updateById(update);
    }

    private void setAuthor(long contentId, long authorId, String authorName) {
        CmsContent update = new CmsContent();
        update.setId(contentId);
        update.setAuthorId(authorId);
        update.setAuthorName(authorName);
        contentMapper.updateById(update);
    }

    private void insertCategoryLink(long contentId, long categoryId, String dimension) {
        CmsContentCategory row = new CmsContentCategory();
        row.setContentId(contentId);
        row.setCategoryId(categoryId);
        row.setDimension(dimension);
        contentCategoryMapper.insert(row);
    }

    private void insertTagLink(long contentId, long tagId) {
        CmsContentTag row = new CmsContentTag();
        row.setContentId(contentId);
        row.setTagId(tagId);
        contentTagMapper.insert(row);
    }

    private long insertMenu(String code, String name) {
        CmsMenu row = new CmsMenu();
        row.setSiteId(siteId);
        row.setCode(code);
        row.setName(name);
        row.setStatus(1);
        row.setSort(1);
        menuMapper.insert(row);
        return row.getId();
    }

    private void insertMenuItem(long menuId, String kind, Long refId, String url, String label, int sort) {
        CmsMenuItem row = new CmsMenuItem();
        row.setMenuId(menuId);
        row.setParentId(0L);
        row.setKind(kind);
        row.setRefId(refId);
        row.setUrl(url);
        row.setLabel(label);
        row.setVisible(1);
        row.setSort(sort);
        menuItemMapper.insert(row);
    }

    private static List<String> titles(List<ContentItem> rows) {
        return rows.stream().map(ContentItem::title).toList();
    }
}
