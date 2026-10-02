package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.ContentQueryRequest;
import com.lingjiuw.cms.module.cms.dto.ContentSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentVO;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.WhereCondition;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContentService} 的真库验收（PostgreSQL）：保存内容 → 断言 {@code cms_content_index} /
 * {@code cms_content_category} / {@code cms_content_tag} 三张派生表被正确重建，且写进去的索引行
 * 能被发布引擎的 {@code where} / {@code orderby} 筛到。
 *
 * <p><b>为什么要真库</b>：C-3 最关键的一条是"索引表被写对"——{@code where} / {@code orderby} /
 * {@code facet} 只读 {@code cms_content_index}（§2.5），值落错列就等于"这个取值筛不到内容"。
 * 索引行的形态只有真库能证明：jsonb 写入、numeric / timestamp 落列、部分唯一索引与逻辑删除的先后顺序。
 * 因此本类沿用 {@code DbContentProviderTest} 的做法：{@code @SpringBootTest}（不起 Web 容器）+
 * {@code @Transactional}（测试结束回滚，不动开发库），{@code spring.flyway.enabled=false} 绕开校验和，
 * 每个用例插一个独立站点，不受库里已有数据干扰。
 *
 * <p><b>用例之间的隔离</b>：站点上下文是 ThreadLocal，每个用例在 {@code setUp} 里重置成自己那个
 * 站点、{@code tearDown} 里清空；已发布内容的 {@code publish_time} 一律取"当前时间减 1 分钟"
 * （引擎的默认时间窗是 {@code status='PUBLISHED' and publish_time <= now()}，§6.3），
 * 不依赖"插入与断言之间时钟怎么走"。
 */
@EnabledIf("databaseReachable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "logging.level.root=warn"})
@Transactional
class ContentServiceTest {

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
    private ContentService contentService;
    @Autowired
    private DbContentProviderFactory providerFactory;
    @Autowired
    private CmsSiteMapper siteMapper;
    @Autowired
    private CmsContentMapper contentMapper;
    @Autowired
    private CmsContentTypeMapper contentTypeMapper;
    @Autowired
    private CmsFieldMapper fieldMapper;
    @Autowired
    private CmsContentIndexMapper contentIndexMapper;
    @Autowired
    private CmsContentCategoryMapper contentCategoryMapper;
    @Autowired
    private CmsContentTagMapper contentTagMapper;
    @Autowired
    private CmsTagMapper tagMapper;
    @Autowired
    private CmsCategoryMapper categoryMapper;

    private long siteId;

    @BeforeEach
    void setUp() {
        CmsSite site = new CmsSite();
        site.setName("内容 CRUD 测试站");
        site.setCode("content-test-" + UUID.randomUUID());
        site.setRootDir("content-test");
        site.setStatus(1);
        site.setIsDefault(0);
        siteMapper.insert(site);
        siteId = site.getId();
        // 上下文每个用例重置一次：上一个用例可能改过它，泄漏进来会让"引擎按站点查"查错站点
        SiteContext.set(siteId);
        // 保存内容要记录 create_by / update_by，走真实的 SecurityUtils 路径
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                LoginUser.builder().id(1L).username("admin").build(), null));
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void 保存内容重建索引表并按类型落值() {
        insertType("article", "文章", "CONTENT");
        insertField("article", "brand", "品牌", "TEXT", 0, 1, null);
        insertField("article", "price", "价格", "DECIMAL", 0, 1, null);
        insertField("article", "stock", "库存", "INT", 0, 1, null);
        insertField("article", "featured", "推荐", "BOOL", 0, 1, null);
        insertField("article", "region", "地区", "ENUM", 0, 1, "huadong:华东,huanan:华南");
        insertField("article", "readingLevel", "阅读级别", "ENUM_MULTI", 0, 1, "beginner:入门,advanced:进阶");
        insertField("article", "relatedProducts", "关联产品", "RELATION", 0, 1, null);
        insertField("article", "startDate", "开始日期", "DATE", 0, 1, null);
        insertField("article", "specs", "规格", "JSON", 0, 1, null);
        // indexed=0：只存 data，不进索引表
        insertField("article", "memo", "备注", "TEXT", 0, 0, null);
        long hot = insertTag("hot", "热点");

        contentService.create(new ContentSaveRequest("article", null, "index-demo", "索引演示", "摘要",
                null, "PUBLISHED", 10, Boolean.TRUE, Boolean.FALSE, null, null,
                Map.of("brand", "Acme", "price", 1299.00, "stock", 12, "featured", 1, "region", "huadong",
                        "readingLevel", List.of("beginner", "advanced"), "relatedProducts", List.of(42),
                        "startDate", "2022-04-18", "specs", List.of(Map.of("key", "规模", "value", "10 万")),
                        "memo", "只在 data 里"),
                "<p>正文 内容</p>", "RICHTEXT", "SEO 标题", "SEO 描述", "k1",
                null, List.of(hot)));

        CmsContent saved = onlyContent("article");
        assertEquals("PUBLISHED", saved.getStatus());
        assertNotNull(saved.getPublishTime(), "PUBLISHED 且未给 publish_time 时填 now()");
        assertEquals(4, saved.getWordCount(), "去标签去空白后的字符数");
        assertTrue(saved.getData().contains("\"brand\": \"Acme\""), saved.getData());

        List<CmsContentIndex> rows = indexRows(saved.getId());
        assertEquals(9, rows.size(),
                "8 个 indexed=1 的字段各写一行，其中 ENUM_MULTI 两个取值两行；JSON 没有可筛取值、"
                        + "indexed=0 的字段都不写");

        assertEquals("default", valueKey(rows, "brand"));
        assertEquals("Acme", strValue(rows, "brand"));
        assertEquals("TEXT", valueType(rows, "brand"));

        assertEquals(0, numValue(rows, "price").compareTo(new BigDecimal("1299.00")),
                "DECIMAL 落 num_value");
        assertEquals(0, numValue(rows, "stock").compareTo(BigDecimal.valueOf(12)), "INT 落 num_value");
        assertEquals(0, numValue(rows, "featured").compareTo(BigDecimal.ONE),
                "BOOL 落 num_value 的 0 / 1");

        assertEquals("huadong", strValue(rows, "region"), "ENUM 存存储值不是标签");
        assertEquals("ENUM", valueType(rows, "region"));

        assertEquals(List.of("advanced", "beginner"), valueKeyList(rows, "readingLevel"),
                "多值字段每个取值一行，value_key 就是取值");
        assertEquals("ENUM_MULTI", valueType(rows, "readingLevel"));

        assertEquals(List.of("42"), valueKeyList(rows, "relatedProducts"));
        assertEquals(0, numValue(rows, "relatedProducts").compareTo(new BigDecimal("42")),
                "RELATION 的每行把目标 id 落 num_value（§2.5）");

        assertEquals("2022-04-18", timeValue(rows, "startDate").toLocalDate().toString(),
                "DATE 落 time_value");
        assertEquals("DATE", valueType(rows, "startDate"));
        // JSON 字段：对象元素取不出可筛的取值，不写索引行（免得 facet 多出无意义的取值）
        assertTrue(rows.stream().noneMatch(row -> "specs".equals(row.getFieldCode())));
        assertTrue(rows.stream().noneMatch(row -> "memo".equals(row.getFieldCode())),
                "indexed=0 的字段不进索引表");
        assertTrue(rows.stream().allMatch(row -> siteId == row.getSiteId()));
        assertTrue(rows.stream().allMatch(row -> "article".equals(row.getTypeCode())));
    }

    @Test
    void JSON与图片字段的取值按可筛形态进索引() {
        insertType("article", "文章", "CONTENT");
        insertField("article", "specs", "规格", "JSON", 0, 1, null);
        insertField("article", "imgs", "图集", "IMAGES", 0, 1, null);

        contentService.create(new ContentSaveRequest("article", null, "json", "JSON 索引", null, null,
                "DRAFT", 0, null, null, null, null,
                Map.of("specs", "一句话", "imgs", List.of(
                        Map.of("url", "/uploads/demo/a.jpg", "alt", "图一"),
                        Map.of("url", "/uploads/demo/b.jpg", "alt", "图二"))),
                null, "RICHTEXT", null, null, null, null, null));

        List<CmsContentIndex> rows = indexRows(onlyContent("article").getId());
        assertEquals("一句话", strValue(rows, "specs"), "JSON 是标量时按字符串落 str_value");
        assertEquals("JSON", valueType(rows, "specs"));
        assertEquals(List.of("/uploads/demo/a.jpg", "/uploads/demo/b.jpg"), valueKeyList(rows, "imgs"),
                "IMAGES 每张图一行，value_key 取媒体的 url");
    }

    @Test
    void 修改内容清掉旧索引再插新索引() {
        insertType("article", "文章", "CONTENT");
        insertField("article", "brand", "品牌", "TEXT", 0, 1, null);
        insertField("article", "stock", "库存", "INT", 0, 1, null);

        contentService.create(new ContentSaveRequest("article", null, "rebuild", "重建索引", null, null,
                "DRAFT", 0, null, null, null, null, Map.of("brand", "Acme", "stock", 5), null, "RICHTEXT",
                null, null, null, null, null));
        long id = onlyContent("article").getId();
        assertEquals(2, indexRows(id).size());

        contentService.update(id, new ContentSaveRequest("article", null, "rebuild", "重建索引", null, null,
                "DRAFT", 0, null, null, null, null, Map.of("brand", "Beta", "stock", 7), null, "RICHTEXT",
                null, null, null, null, null));

        List<CmsContentIndex> rows = indexRows(id);
        assertEquals(2, rows.size(), "旧索引行被清掉，不会越攒越多（部分唯一索引也不允许同键两行）");
        assertEquals("Beta", strValue(rows, "brand"));
        assertTrue(rows.stream().noneMatch(row -> "Acme".equals(row.getStrValue())), "旧取值不再可筛");
        assertEquals(0, numValue(rows, "stock").compareTo(BigDecimal.valueOf(7)));

        // 字段值整体清空：索引行随之全部消失
        contentService.update(id, new ContentSaveRequest("article", null, "rebuild", "重建索引", null, null,
                "DRAFT", 0, null, null, null, null, Map.of(), null, "RICHTEXT",
                null, null, null, null, null));
        assertTrue(indexRows(id).isEmpty());
    }

    @Test
    void 分类恰好一行主分类标签关联一并维护() {
        insertType("article", "文章", "CONTENT");
        long news = insertCategory("news", "新闻");
        long tech = insertCategory("tech", "技术");
        long hot = insertTag("hot", "热点");
        long javaTag = insertTag("java", "Java");

        contentService.create(new ContentSaveRequest("article", null, "relations", "关联", null, null,
                "DRAFT", 0, null, null, null, null, Map.of(), null, "RICHTEXT", null, null, null,
                List.of(news, tech), List.of(hot, javaTag)));

        long id = onlyContent("article").getId();
        List<CmsContentCategory> relations = contentCategoryMapper.selectList(
                Wrappers.<CmsContentCategory>lambdaQuery().eq(CmsContentCategory::getContentId, id));
        assertEquals(2, relations.size());
        assertEquals(1, relations.stream().filter(row -> "primary".equals(row.getDimension())).count(),
                "第一个分类是主分类，恰好一行");
        assertEquals(news, relations.stream()
                .filter(row -> "primary".equals(row.getDimension())).findFirst().orElseThrow()
                .getCategoryId());
        assertEquals(tech, relations.stream()
                .filter(row -> "secondary".equals(row.getDimension())).findFirst().orElseThrow()
                .getCategoryId());
        assertEquals(2, contentTagMapper.selectCount(Wrappers.<CmsContentTag>lambdaQuery()
                .eq(CmsContentTag::getContentId, id)));

        // 改成只留一个分类：旧的 primary 与那两个标签都要被清掉
        contentService.update(id, new ContentSaveRequest("article", null, "relations", "关联", null, null,
                "DRAFT", 0, null, null, null, null, Map.of(), null, "RICHTEXT", null, null, null,
                List.of(tech), List.of(javaTag)));
        List<CmsContentCategory> after = contentCategoryMapper.selectList(
                Wrappers.<CmsContentCategory>lambdaQuery().eq(CmsContentCategory::getContentId, id));
        assertEquals(1, after.size());
        assertEquals("primary", after.get(0).getDimension());
        assertEquals(tech, after.get(0).getCategoryId());
        assertEquals(1, contentTagMapper.selectCount(Wrappers.<CmsContentTag>lambdaQuery()
                .eq(CmsContentTag::getContentId, id)));

        // 详情把关联 id 一起给前端（编辑页回填）
        ContentVO vo = contentService.detail(id);
        assertEquals(List.of(tech), vo.categoryIds());
        assertEquals(List.of(javaTag), vo.tagIds());
    }

    @Test
    void 保存的索引行能被发布引擎筛到与排序() {
        insertType("service", "服务项", "CONTENT");
        insertField("service", "group", "服务分类", "ENUM", 0, 1, "dev:应用开发,ops:顾问与运维");
        insertField("service", "rank", "栏内顺序", "INT", 0, 1, null);
        insertField("service", "levels", "等级", "ENUM_MULTI", 0, 1, "a:甲,b:乙");

        insertPublished("service", "服务一", Map.of("group", "dev", "rank", 10, "levels", List.of("a")));
        insertPublished("service", "服务二", Map.of("group", "dev", "rank", 30, "levels", List.of("a", "b")));
        insertPublished("service", "服务三", Map.of("group", "ops", "rank", 20, "levels", List.of("b")));

        // 这三条断言走的是发布引擎的取数出口：where / orderby 只读 cms_content_index（§2.5），
        // 索引行的列写错（num_value / str_value / time_value 落错）在这里就筛不出来
        ContentProvider provider = providerFactory.forSite(siteId);
        assertEquals(3L, provider.count(new ContentQuery().type("service").category("all")),
                "先确认三条已发布内容本身能被引擎看到");

        ContentQuery query = new ContentQuery().type("service").category("all")
                .where(List.of(new WhereCondition("group", WhereCondition.Op.eq, List.of("dev"))))
                .orderby(List.of(new OrderBy("rank", false)));
        assertEquals(List.of("服务一", "服务二"),
                provider.query(query).rows().stream().map(ContentItem::title).toList(),
                "ENUM 索引进 str_value、INT 索引进 num_value：筛得到且排得动");

        assertEquals(0L, provider.count(new ContentQuery().type("service").category("all")
                        .where(List.of(new WhereCondition("group", WhereCondition.Op.eq, List.of("nope"))))),
                "没有的取值一条都不匹配");

        for (String value : List.of("a", "b")) {
            assertEquals(2L, provider.count(new ContentQuery().type("service").category("all")
                            .where(List.of(new WhereCondition("levels", WhereCondition.Op.eq,
                                    List.of(value))))),
                    "ENUM_MULTI 的取值 " + value + " 各是一行索引（a 命中两条、b 命中两条）");
        }
    }

    @Test
    void data按字段定义校验且只存声明过的字段() {
        insertType("article", "文章", "CONTENT");
        insertField("article", "brand", "品牌", "TEXT", 1, 1, null);          // required
        insertField("article", "region", "地区", "ENUM", 0, 1, "huadong:华东");
        insertField("article", "readingLevel", "阅读级别", "ENUM_MULTI", 0, 1, "beginner:入门");
        insertField("article", "stock", "库存", "INT", 0, 1, null);

        assertTrue(assertThrows(BizException.class, () -> createDraft("article", Map.of())).getMessage()
                .contains("品牌"), "required 字段缺失要拦住");
        assertTrue(assertThrows(BizException.class,
                () -> createDraft("article", Map.of("brand", "Acme", "region", "not-an-option")))
                .getMessage().contains("选项"), "ENUM 取值必须在 options 里");
        assertTrue(assertThrows(BizException.class,
                () -> createDraft("article", Map.of("brand", "Acme", "stock", "abc"))).getMessage()
                .contains("整数"), "INT 形态不对要拦住");

        // 多值字段收单个取值也接受（前端把多选收敛成一个值时不用报错）
        createDraft("article", Map.of("brand", "Acme", "region", "huadong", "readingLevel", "beginner",
                "unknownField", "x"));
        CmsContent saved = onlyContent("article");
        assertTrue(saved.getData().contains("beginner"));
        assertFalse(saved.getData().contains("unknownField"), "表外的键不落库（§2.3：只存声明过的字段）");
        assertEquals(1, contentIndexMapper.selectCount(Wrappers.<CmsContentIndex>lambdaQuery()
                .eq(CmsContentIndex::getContentId, saved.getId())
                .eq(CmsContentIndex::getFieldCode, "readingLevel")));

        assertTrue(assertThrows(BizException.class, () -> contentService.create(
                        new ContentSaveRequest("nope", null, "s", "类型不存在", null, null, "DRAFT", 0,
                                null, null, null, null, Map.of(), null, "RICHTEXT", null, null, null,
                                null, null)))
                .getMessage().contains("类型不存在"), "type_code 必须是当前站点存在的类型");
    }

    @Test
    void 单例类型至多一条内容() {
        insertType("home", "首页文案", "SINGLE");
        createDraft("home", Map.of());
        assertTrue(assertThrows(BizException.class, () -> createDraft("home", Map.of())).getMessage()
                .contains("只能有一条"), "自定义 SINGLE 类型由业务层拦住第二条");

        // 改自己不算第二条
        long id = onlyContent("home").getId();
        contentService.update(id, new ContentSaveRequest("home", null, null, "首页文案 v2", null, null,
                "DRAFT", 0, null, null, null, null, Map.of(), null, "RICHTEXT",
                null, null, null, null, null));
        assertEquals("首页文案 v2", onlyContent("home").getTitle());
    }

    @Test
    void 详情与列表按站点隔离() {
        insertType("article", "文章", "CONTENT");
        createDraft("article", Map.of());
        long id = onlyContent("article").getId();
        assertNotNull(contentService.detail(id));

        // 换一个站点：同 id 的内容"不存在"，列表也看不到
        SiteContext.set(siteId + 100_000L);
        assertTrue(assertThrows(BizException.class, () -> contentService.detail(id)).getMessage()
                .contains("不存在"));
        assertEquals(0, contentService.page(new ContentQueryRequest(1, 20, "article", null, null))
                .getTotal());
    }

    /* ---------------- fixture ---------------- */

    private void createDraft(String typeCode, Map<String, Object> data) {
        contentService.create(new ContentSaveRequest(typeCode, null, "slug-" + UUID.randomUUID(),
                "标题", null, null, "DRAFT", 0, null, null, null, null, data, null, "RICHTEXT",
                null, null, null, null, null));
    }

    /**
     * 建一条"引擎一定看得见"的内容：{@code publish_time} 取当前时间减一分钟。
     * 引擎的默认时间窗是 {@code status='PUBLISHED' and publish_time <= now()}（§6.3），
     * 用过去的固定点就不依赖"插入与断言之间时钟怎么走"。
     */
    private void insertPublished(String typeCode, String title, Map<String, Object> data) {
        contentService.create(new ContentSaveRequest(typeCode, null, "slug-" + UUID.randomUUID(),
                title, null, null, "PUBLISHED", 0, null, null,
                LocalDateTime.now().minusMinutes(1), null, data, "正文", "RICHTEXT",
                null, null, null, null, null));
    }

    private void insertType(String code, String name, String kind) {
        CmsContentType row = new CmsContentType();
        row.setSiteId(siteId);
        row.setCode(code);
        row.setName(name);
        row.setKind(kind);
        row.setHierarchical(0);
        row.setPerPage(20);
        row.setStatus(1);
        row.setSort(0);
        contentTypeMapper.insert(row);
    }

    private void insertField(String typeCode, String code, String label, String fieldType,
                             int required, int indexed, String options) {
        CmsField row = new CmsField();
        row.setSiteId(siteId);
        row.setTypeCode(typeCode);
        row.setCode(code);
        row.setLabel(label);
        row.setFieldType(fieldType);
        row.setRaw(0);
        row.setRequired(required);
        row.setIndexed(indexed);
        row.setSearchable(0);
        row.setCrossSite(0);
        row.setOptions(options);
        row.setSort(0);
        fieldMapper.insert(row);
    }

    private long insertCategory(String slug, String name) {
        CmsCategory row = new CmsCategory();
        row.setSiteId(siteId);
        row.setParentId(0L);
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

    private CmsContent onlyContent(String typeCode) {
        List<CmsContent> rows = contentMapper.selectList(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getTypeCode, typeCode));
        assertEquals(1, rows.size(), "这个站点 + 类型下应当恰好一条内容");
        return rows.get(0);
    }

    private List<CmsContentIndex> indexRows(long contentId) {
        return contentIndexMapper.selectList(Wrappers.<CmsContentIndex>lambdaQuery()
                .eq(CmsContentIndex::getContentId, contentId));
    }

    private static CmsContentIndex row(List<CmsContentIndex> rows, String fieldCode) {
        return rows.stream().filter(row -> fieldCode.equals(row.getFieldCode())).findFirst().orElseThrow();
    }

    private static String valueKey(List<CmsContentIndex> rows, String fieldCode) {
        return row(rows, fieldCode).getValueKey();
    }

    private static List<String> valueKeyList(List<CmsContentIndex> rows, String fieldCode) {
        return rows.stream().filter(row -> fieldCode.equals(row.getFieldCode()))
                .map(CmsContentIndex::getValueKey).sorted().toList();
    }

    private static String valueType(List<CmsContentIndex> rows, String fieldCode) {
        return row(rows, fieldCode).getValueType();
    }

    private static String strValue(List<CmsContentIndex> rows, String fieldCode) {
        return row(rows, fieldCode).getStrValue();
    }

    private static BigDecimal numValue(List<CmsContentIndex> rows, String fieldCode) {
        return row(rows, fieldCode).getNumValue();
    }

    private static LocalDateTime timeValue(List<CmsContentIndex> rows, String fieldCode) {
        return row(rows, fieldCode).getTimeValue();
    }
}
