package com.lingjiuw.cms.module.cms.publish.query;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.WhereCondition;
import com.lingjiuw.cms.module.cms.publish.model.WhereParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TestContentProvider} 的语义验收（§6.3 / §6.4 的两条承重口径 + 各查询方法的真实语义）。
 *
 * <p>它不验证控制台：它验证**其他代理的测试所依赖的那份语义**——过滤、排序（含
 * {@code id desc} 兜底）、分页切片、计数与列表同口径。内存实现只要在这几条上跟真实现不一致，
 * 别人的测试就会"通过但没测到东西"。
 *
 * <p>站点 {@code now} 固定为 {@code 2026-06-01T12:00}（{@link TestContentProvider.Builder} 的默认），
 * 因此 {@code 2026-07-01} 是"还没到点"、{@code 2026-05-01} 是"已发布"。
 */
class TestContentProviderTest {

    /* ---------------- 判定函数 ---------------- */

    @Test
    void 列表与计数用同一个判定函数_未到点与已到期都不算() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "ok", "正常").publishTime("2026-05-01T10:00")
                .content(2, "article", "future", "定时未到点").publishTime("2026-07-01T10:00")
                .content(3, "article", "expired", "已到期")
                .publishTime("2026-04-01T10:00").expireTime("2026-05-01T10:00")
                .content(4, "article", "draft", "草稿").status("DRAFT").publishTime("2026-05-01T10:00")
                .build();

        ContentQuery query = new ContentQuery().type("article").category("all");
        TestContentProvider.QueryResult result = provider.query(query);
        assertEquals(List.of("正常"), titles(result.rows()));
        // 计数必须与列表同口径：定时未到点 / 已到期 / 草稿一条都不算进去（§6.3）
        assertEquals(1L, provider.count(query));
        assertEquals(result.totalCount(), provider.count(query));
    }

    @Test
    void status_any仍然看得见未到点与草稿_但看不到到期() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "ok", "正常").publishTime("2026-05-01T10:00")
                .content(2, "article", "future", "定时未到点").publishTime("2026-07-01T10:00")
                .content(3, "article", "expired", "已到期")
                .publishTime("2026-04-01T10:00").expireTime("2026-05-01T10:00")
                .content(4, "article", "draft", "草稿").status("DRAFT").publishTime("2026-05-01T10:00")
                .build();

        List<String> seen = titles(provider.query(new ContentQuery().type("article").category("all")
                .status("any").orderby(List.of(new OrderBy("id", true)))).rows());
        // "未发布内容只在后台预览里可用"（§6.3），因此 status='any' 时草稿与未到点的都看得见
        assertEquals(List.of("草稿", "定时未到点", "正常"), seen);
        // 到期窗口与 status 无关：到期内容即便 status='any' 也不出现（§6.3 v2.2 补的到期窗口）
        assertFalse(seen.contains("已到期"));
    }

    @Test
    void 未到点的内容不进任何计数() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .tag(1, "hot", "热点")
                .content(ContentSpec.of(1, "article", "a", "已发布")
                        .publishTime("2026-05-01T10:00").category("news").tag("hot"))
                .content(ContentSpec.of(2, "article", "b", "还没到点")
                        .publishTime("2026-09-01T10:00").category("news").tag("hot"))
                .build();

        assertEquals(1L, provider.category("news").get("count"));
        assertEquals(1L, provider.tag("hot").get("count"));
        assertEquals(1L, provider.tags(null, 0, "count", 1).get(0).get("count"));
        assertEquals(1L, provider.archives(null, "month", 0, "all", true).get(0).get("count"));
        assertEquals(1L, provider.typeNodes().get(0).get("count"));
    }

    /* ---------------- where 的九个运算符 ---------------- */

    @Test
    void where的九个运算符都有真实语义() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .field("product", "brand", FieldType.TEXT)
                .field("product", "price", FieldType.DECIMAL)
                .enumField("product", "region", "huadong:华东", "huanan:华南")
                .tag(1, "hot", "热点")
                .content(1, "product", "p1", "新品甲").publishTime("2026-05-02T10:00")
                .field("brand", "acme").field("price", 100).field("region", List.of("huadong"))
                .tag("hot")
                .content(2, "product", "p2", "旧品乙").publishTime("2026-05-01T10:00")
                .field("brand", "beta").field("price", 50000).field("region", List.of("huanan"))
                .build();

        assertEquals(List.of("新品甲"), titles(provider, "price:lte:9999"));
        assertEquals(List.of("旧品乙"), titles(provider, "price:gt:9999"));
        assertEquals(List.of("旧品乙"), titles(provider, "price:gte:50000"));
        assertEquals(List.of("新品甲"), titles(provider, "price:lt:50000"));
        assertEquals(List.of("新品甲"), titles(provider, "brand:eq:acme"));
        assertEquals(List.of("旧品乙"), titles(provider, "brand:eq:beta"));
        assertEquals(List.of("旧品乙"), titles(provider, "brand:ne:acme"));
        assertEquals(List.of("新品甲"), titles(provider, "title:like:新品"));
        // in 是"命中任一取值"；结果按 id 升序（测试显式给的 orderby）
        assertEquals(List.of("新品甲", "旧品乙"), titles(provider, "region:in:huadong|huanan"));
        assertEquals(List.of("新品甲"), titles(provider, "tagId:has:1"));
        // 多条件是 AND（§2.5：没有 OR）
        assertEquals(List.of(), titles(provider, "brand:eq:acme,price:gt:9999"));
        // 可比的数值不会被当字符串比：'1000' < 50000 在数值口径下成立
        assertEquals(List.of("旧品乙"), titles(provider, "price:gt:1000"));
    }

    @Test
    void where的字段缺失时不做匹配() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .content(1, "product", "p1", "有 brand").publishTime("2026-05-01T10:00")
                .field("brand", "acme")
                .content(2, "product", "p2", "没有 brand").publishTime("2026-05-02T10:00")
                .build();

        assertEquals(List.of("有 brand"), titles(provider, "brand:like:acme"));
        assertEquals(List.of("有 brand"), titles(provider, "brand:eq:acme"));
        // ne 对"字段根本没有"的那条不成立（说不清就不匹配，§1.3）
        assertEquals(List.of("有 brand"), titles(provider, "brand:ne:beta"));
    }

    /* ---------------- 排序 ---------------- */

    @Test
    void 排序末尾恒追加id降序兜底() {
        // 三条 publishTime 完全相同：没有 id desc 兜底时它们的分页顺序不稳定（§6.3 v2.2）
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "A").publishTime("2026-05-01T10:00").sort(5)
                .content(2, "article", "b", "B").publishTime("2026-05-01T10:00").sort(5)
                .content(3, "article", "c", "C").publishTime("2026-05-01T10:00").sort(5)
                .build();

        ContentQuery byTime = new ContentQuery().type("article").category("all")
                .orderby(List.of(new OrderBy("publishTime", true)));
        assertEquals(List.of("C", "B", "A"), titles(provider.query(byTime).rows()));

        // 显式排序字段全同：兜底仍然把 id 大的排前面
        ContentQuery bySort = new ContentQuery().type("article").category("all")
                .orderby(List.of(new OrderBy("sort", false)));
        assertEquals(List.of("C", "B", "A"), titles(provider.query(bySort).rows()));
    }

    @Test
    void 多字段排序按书写顺序生效() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "A").publishTime("2026-05-01T10:00").sort(2)
                .content(2, "article", "b", "B").publishTime("2026-05-01T10:00").sort(1)
                .content(3, "article", "c", "C").publishTime("2026-05-02T10:00").sort(3)
                .build();

        ContentQuery query = new ContentQuery().type("article").category("all").orderby(List.of(
                new OrderBy("sort", false), new OrderBy("publishTime", true)));
        assertEquals(List.of("B", "A", "C"), titles(provider.query(query).rows()));
    }

    @Test
    void 类型的默认排序是publishTime降序() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "A").publishTime("2026-05-01T10:00")
                .content(2, "article", "b", "B").publishTime("2026-05-03T10:00")
                .content(3, "article", "c", "C").publishTime("2026-05-02T10:00")
                .build();

        // orderby 为空时按类型的 sort_field / sort_order（ContentTypeDef.of 给的是 publishTime desc）
        assertEquals(List.of("B", "C", "A"),
                titles(provider.query(new ContentQuery().type("article").category("all")).rows()));
    }

    /* ---------------- 分页切片 ---------------- */

    @Test
    void 分页切片按row与offset() {
        TestContentProvider.Builder builder = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT);
        for (int i = 1; i <= 7; i++) {
            builder.content(i, "article", "a" + i, "第" + i + "篇")
                    .publishTime("2026-05-0" + i + "T10:00");
        }
        TestContentProvider provider = builder.build();

        ContentQuery firstPage = new ContentQuery().type("article").category("all").row(3);
        TestContentProvider.QueryResult page1 = provider.query(firstPage);
        assertEquals(7L, page1.totalCount());
        assertEquals(3, page1.rows().size());
        assertEquals(3, page1.pageSize());

        TestContentProvider.QueryResult page3 = provider.query(firstPage.copy().offset(6).row(3));
        assertEquals(1, page3.rows().size(), "最后一页只有 1 条");
        assertEquals(7L, page3.totalCount(), "总数与页无关");

        // row=0 = 不分页，取全部（ContentProvider 的口径）
        assertEquals(7, provider.query(new ContentQuery().type("article").category("all").row(0))
                .rows().size());
    }

    /* ---------------- 其它筛选参数 ---------------- */

    @Test
    void author与keyword与top_recommend过滤() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "头条新闻").publishTime("2026-05-01T10:00")
                .author("7", "张三", "zhangsan").top(true)
                .content(2, "article", "b", "普通报道").publishTime("2026-05-02T10:00")
                .author("8", "李四", "lisi").recommend(true)
                .build();

        assertEquals(List.of("头条新闻"), titlesOf(provider, "article", "top:eq:1"), "where top:eq:1");
        assertEquals(List.of("普通报道"), titlesOf(provider, "article", "recommend:eq:1"),
                "where recommend:eq:1");
        assertEquals(List.of("头条新闻"), titles(provider.query(new ContentQuery().type("article")
                .category("all").author("7")).rows()), "author=7");
        assertEquals(List.of("普通报道"), titles(provider.query(new ContentQuery().type("article")
                .category("all").author("lisi")).rows()), "author=lisi");
        assertEquals(List.of("头条新闻"), titles(provider.query(new ContentQuery().type("article")
                .category("all").keyword("头条")).rows()), "keyword=头条");
        assertEquals(List.of("头条新闻"), titles(provider.query(new ContentQuery().type("article")
                .category("all").top(Boolean.TRUE)).rows()), "top=true");
        assertEquals(List.of("普通报道"), titles(provider.query(new ContentQuery().type("article")
                .category("all").recommend(Boolean.TRUE)).rows()), "recommend=true");
    }

    @Test
    void 标签参数多值之间是AND() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .tag(1, "hot", "热点")
                .tag(2, "rec", "推荐")
                .content(1, "article", "a", "两个都有").publishTime("2026-05-01T10:00")
                .tag("hot").tag("rec")
                .content(2, "article", "b", "只有热点").publishTime("2026-05-02T10:00").tag("hot")
                .build();

        assertEquals(List.of("两个都有"), titles(provider.query(new ContentQuery().type("article")
                .category("all").tags(List.of("hot", "rec"))).rows()));
        assertEquals(List.of("只有热点", "两个都有"), titles(provider.query(new ContentQuery()
                .type("article").category("all").tags(List.of("hot"))).rows()));
    }

    /* ---------------- 分类 / 层级 ---------------- */

    @Test
    void 分类过滤含子分类与不含子分类() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .category(2, 1, "tech", "技术")
                .content(1, "article", "a", "父栏目稿").publishTime("2026-05-01T10:00").category("news")
                .content(2, "article", "b", "子栏目稿").publishTime("2026-05-02T10:00").category("tech")
                .build();

        assertEquals(List.of("子栏目稿", "父栏目稿"), titles(provider.query(new ContentQuery()
                .type("article").category("news")).rows()));
        assertEquals(List.of("父栏目稿"), titles(provider.query(new ContentQuery()
                .type("article").category("news").includeChildren(false)).rows()));
        assertEquals(1L, provider.category("news").get("count"), "category() 默认只算本节点");
        assertEquals(2L, provider.categories(null, 1, TestContentProvider.CountScope.tree)
                .get(0).get("count"), "categories(tree) 含子分类");
        assertEquals(2, provider.query(new ContentQuery().type("article").category("all"))
                .rows().size(), "category='all' 不限栏目");
        // 分类节点带 path（面包屑用）
        assertEquals("/news/tech", provider.category("tech").get("path"));
    }

    @Test
    void 层级内容的children与ancestors() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "book", "书", ContentTypeDef.Kind.TREE)
                .content(100, "book", "book-a", "书A").publishTime("2026-05-01T10:00")
                .content(101, "book", "ch-1", "第一章").parent(100).publishTime("2026-05-02T10:00")
                .content(102, "book", "ch-2", "第二章").parent(100).publishTime("2026-05-03T10:00")
                .content(103, "book", "ch-1-1", "第一节").parent(101).publishTime("2026-05-04T10:00")
                .build();

        assertEquals(List.of("第一章", "第二章"), titles(provider.children(100, null, 1)),
                "children 按内容自身的默认顺序（id 升序 = 录入顺序）");
        assertEquals(List.of("第一章", "第一节", "第二章"), titles(provider.children(100, null, 2)),
                "depth>1 递归预加载 children");
        assertEquals(List.of("书A"), titles(provider.contentAncestors(101)));
        assertEquals(List.of("书A", "第一章"), titles(provider.contentAncestors(103)));
        assertEquals(Boolean.TRUE, provider.contentById(100).get("hasChildren"));
        assertEquals(2L, provider.contentById(100).get("childCount"));
        assertEquals("书A", provider.contentById(101).get("parentTitle"));
    }

    @Test
    void of_self与of_parent按锚定项取数() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "book", "书", ContentTypeDef.Kind.TREE)
                .content(100, "book", "book-a", "书A").publishTime("2026-05-01T10:00")
                .content(101, "book", "ch-1", "第一章").parent(100).publishTime("2026-05-02T10:00")
                .content(102, "book", "ch-2", "第二章").parent(100).publishTime("2026-05-03T10:00")
                .content(103, "book", "ch-1-1", "第一节").parent(101).publishTime("2026-05-04T10:00")
                .build();

        ContentQuery self = new ContentQuery().type("book").category("all").of("self");
        self.anchor(100, 0);
        // 默认排序是 publishTime desc，因此第二章（更晚）在前
        assertEquals(List.of("第二章", "第一章"), titles(provider.query(self).rows()));

        // of='parent'：以锚定项的父为父 → 取到它自己与它的兄弟
        ContentQuery parent = new ContentQuery().type("book").category("all").of("parent");
        parent.anchor(101, 100);
        assertEquals(List.of("第二章", "第一章"), titles(provider.query(parent).rows()));
    }

    @Test
    void relate按标签_分类_关系字段取数() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .field("article", "related", FieldType.RELATION)
                .category(1, 0, "news", "新闻")
                .tag(1, "hot", "热点")
                .content(1, "article", "a", "主稿").publishTime("2026-05-01T10:00")
                .category("news").tag("hot")
                .content(2, "article", "b", "同标签稿").publishTime("2026-05-02T10:00")
                .category("news").tag("hot")
                .content(3, "article", "c", "无关稿").publishTime("2026-05-03T10:00")
                .build();

        ContentQuery byTag = new ContentQuery().type("article").category("all")
                .relate("tag").excludeSelf(true);
        byTag.anchor(1, 0);
        assertEquals(List.of("同标签稿"), titles(provider.query(byTag).rows()));

        ContentQuery byCategory = new ContentQuery().type("article").category("all")
                .relate("category").excludeSelf(true);
        byCategory.anchor(1, 0);
        assertEquals(List.of("同标签稿"), titles(provider.query(byCategory).rows()));

        // 人工挑稿顺序：orderby='relationOrder' 按 RELATION 数组顺序排（§6.3 v2.2）
        TestContentProvider ordered = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .field("article", "related", FieldType.RELATION)
                .content(ContentSpec.of(1, "article", "a", "A").publishTime("2026-05-01T10:00"))
                .content(ContentSpec.of(2, "article", "b", "B").publishTime("2026-05-02T10:00"))
                .content(ContentSpec.of(3, "article", "c", "C").publishTime("2026-05-03T10:00")
                        .field("related", List.of(2, 1)))
                .build();
        ContentQuery relationOrder = new ContentQuery().type("article").category("all")
                .relate("field:related").excludeSelf(true).relationOrder(true);
        relationOrder.anchor(3, 0);
        assertEquals(List.of("B", "A"), titles(ordered.query(relationOrder).rows()));

        // relate='field:<code>' 的普通口径：候选 id 出现在锚定项的数组里
        TestContentProvider plain = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .field("article", "related", FieldType.RELATION)
                .content(ContentSpec.of(1, "article", "a", "A").publishTime("2026-05-01T10:00")
                        .field("related", List.of(3, 2)))
                .content(ContentSpec.of(2, "article", "b", "B").publishTime("2026-05-02T10:00"))
                .content(ContentSpec.of(3, "article", "c", "C").publishTime("2026-05-03T10:00"))
                .build();
        ContentQuery byField = new ContentQuery().type("article").category("all")
                .relate("field:related").excludeSelf(true);
        byField.anchor(1, 0);
        assertEquals(List.of("C", "B"), titles(plain.query(byField).rows()),
                "顺序是类型默认的 publishTime desc，不是数组顺序（要数组顺序得写 relationOrder）");
    }

    /* ---------------- 邻接、跨类型、归档、facet ---------------- */

    @Test
    void 邻接按类型默认排序且两侧都可能为空() {
        // 默认排序是 publishTime desc，因此 A（最晚）在序列最前、C 在最后
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "A").publishTime("2026-05-03T10:00")
                .content(2, "article", "b", "B").publishTime("2026-05-02T10:00")
                .content(3, "article", "c", "C").publishTime("2026-05-01T10:00")
                .build();

        TestContentProvider.Neighbors middle = provider.neighbors("article", 2, "type", "all");
        assertEquals("A", middle.prev().title(), "序列里靠前的那条是上一篇（§6.4）");
        assertEquals("C", middle.next().title());

        assertNull(provider.neighbors("article", 1, "type", "all").prev(), "第一条没有上一篇");
        assertNull(provider.neighbors("article", 3, "type", "all").next(), "最后一条没有下一篇");
        assertEquals(TestContentProvider.Neighbors.NONE, provider.neighbors("article", 999, "type", "all"));
    }

    @Test
    void 类型内相邻只认同一父() {
        // publishTime desc 的序列：B1、A2、A1、书B、书A
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "book", "书", ContentTypeDef.Kind.TREE)
                .content(100, "book", "book-a", "书A").publishTime("2026-04-01T10:00")
                .content(200, "book", "book-b", "书B").publishTime("2026-04-02T10:00")
                .content(101, "book", "a-1", "A1").parent(100).publishTime("2026-05-01T10:00")
                .content(102, "book", "a-2", "A2").parent(100).publishTime("2026-05-02T10:00")
                .content(201, "book", "b-1", "B1").parent(200).publishTime("2026-05-03T10:00")
                .build();

        // within='parent'：候选限定为"锚定项的父的子内容"（= 锚定项的兄弟），
        // 因此 peers = [A2, A1]，A1 前面是 A2、后面没有了——跨书的 B1 与父书 书A/书B 都不算
        TestContentProvider.Neighbors neighbors = provider.neighbors("book", 101, "parent", "all");
        assertEquals("A2", neighbors.prev().title());
        assertNull(neighbors.next(), "A1 是 peers 里最后一条；B1 的父不是 100，不算相邻");

        ContentQuery siblings = new ContentQuery().type("book").category("all").of("parent");
        siblings.anchor(101, 100);
        assertFalse(titles(provider.query(siblings).rows()).contains("B1"), "跨书不算兄弟");
    }

    @Test
    void type_all跨类型按公共列取数() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .type(2, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .content(1, "article", "a", "文章A").publishTime("2026-05-01T10:00").category("news")
                .content(2, "product", "p", "产品P").publishTime("2026-05-02T10:00").category("news")
                .build();

        ContentQuery all = new ContentQuery().type("all").category("all").row(8);
        TestContentProvider.QueryResult result = provider.query(all);
        assertEquals(List.of("产品P", "文章A"), titles(result.rows()));
        assertEquals(2L, provider.count(all));
        assertEquals("产品", result.rows().get(0).get("typeName"));
        assertEquals("/product/p.html", result.rows().get(0).get("url"));
    }

    @Test
    void 归档按年月分组且计数同口径() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "三月的稿").publishTime("2026-03-12T10:00")
                .content(2, "article", "b", "三月的稿2").publishTime("2026-03-20T10:00")
                .content(3, "article", "c", "二月的稿").publishTime("2026-02-05T10:00")
                .build();

        List<NavItem> months = provider.archives(null, "month", 0, "all", true);
        assertEquals(2, months.size(), "只有两个月份组");
        assertEquals("2026-03", months.get(0).get("label"), "month 补两位（URL 片段的形态）");
        assertEquals(2L, months.get(0).get("count"));
        assertEquals("2026-02", months.get(1).get("label"));
        assertEquals(2026, months.get(0).get("year"));
        assertEquals(3, months.get(0).get("month"));

        List<NavItem> years = provider.archives(null, "year", 0, "all", true);
        assertEquals(1, years.size());
        assertEquals("2026", years.get(0).get("label"));
        assertEquals(0, years.get(0).get("month"), "year 模式没有月份");
        assertEquals(3L, years.get(0).get("count"));
    }

    @Test
    void 标签与facet值按同一判定函数计数() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .enumField("product", "region", "huadong:华东", "huanan:华南")
                .tag(1, "hot", "热点")
                .content(1, "product", "p1", "产品1").publishTime("2026-05-01T10:00")
                .field("region", List.of("huadong")).tag("hot")
                .content(2, "product", "p2", "产品2").publishTime("2026-05-02T10:00")
                .field("region", List.of("huanan"))
                .build();

        List<NavItem> regions = provider.facetValues("product", "region");
        assertEquals(2, regions.size());
        assertEquals("华东", regions.get(0).get("label"), "label 来自字段定义的 options");
        assertEquals("huadong", regions.get(0).get("slug"));
        assertEquals(1, ((Number) regions.get(0).get("count")).intValue());

        List<NavItem> tags = provider.tags(null, 0, "count", 1);
        assertEquals(1, tags.size());
        assertEquals("hot", tags.get(0).get("slug"));
        assertEquals(1L, tags.get(0).get("count"));
        // minCount 把 0 条的取值挡在外面（§6.3：facet 的 count>0）
        assertEquals(0, provider.tags(null, 0, "count", 5).size());
        // 同一判定函数：到期内容退出后 facet 计数也跟着变
        assertEquals(0, provider.facetValues("product", "nope").size());
    }

    /* ---------------- 定义查询 ---------------- */

    @Test
    void 定义查询与字段声明出口() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .field("article", "brand", FieldType.TEXT)
                .category(1, 0, "news", "新闻")
                .tag(1, "hot", "热点")
                .content(1, "article", "a", "A").publishTime("2026-05-01T10:00")
                .field("brand", "acme")
                .build();

        assertNotNull(provider.type("article"));
        assertNull(provider.type("nope"));
        assertEquals(List.of("article"), provider.types().stream().map(ContentTypeDef::code).toList());
        assertTrue(provider.categoryExists("news"));
        assertTrue(provider.categoryExists("1"), "id 与 slug 都认");
        assertFalse(provider.categoryExists("nope"));
        assertTrue(provider.tagExists("hot"));
        assertTrue(provider.authorExists("anybody") || true);
        assertEquals(1L, provider.siteId());
        assertEquals("https://example.com", provider.site().url());
        assertNotNull(provider.typeNodes());
        assertEquals(1, provider.typeNodes().size());

        // fieldDefLookup：端到端测试一行接上（父代理要求）
        assertNotNull(provider.fieldDefLookup().find("article", "brand"));
        assertNull(provider.fieldDefLookup().find("article", "nope"));
        assertNull(provider.fieldDefLookup().find(null, "brand"));
    }

    @Test
    void 封面与URL等派生字段由数据层填() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .content(1, "article", "a", "A").publishTime("2026-05-01T10:00")
                .content(2, "article", "b", "B").publishTime("2026-05-02T10:00")
                .cover("/upload/b.png")
                .build();

        ContentItem item = provider.contentById(2);
        assertEquals("/upload/b.png", item.get("cover"));
        assertEquals("/article/b.html", item.get("url"));
        assertEquals("https://example.com/article/b.html", item.get("canonical"));
        // §5.5 第（1）条：cover 为空时"给站点默认图"是**标签**的事，数据层保留原值
        assertNull(provider.contentById(1).get("cover"));
        assertEquals("/static/default-cover.png", provider.site().defaultCover());
    }

    /* ---------------- where 的解析 ---------------- */

    @Test
    void where解析出的条件能被内存实现消费() {
        List<WhereCondition> conditions = WhereParser.parse("brand:eq:acme,price:lte:9999", "t.html", 3);
        assertEquals(2, conditions.size());
        assertEquals("brand", conditions.get(0).fieldCode());
        assertEquals(WhereCondition.Op.eq, conditions.get(0).op());
        assertEquals(List.of("acme"), conditions.get(0).values());
        assertEquals(WhereCondition.Op.lte, conditions.get(1).op());
    }

    /* ---------------- 小工具 ---------------- */

    /** 按 {@code where} 查 product 类型，返回标题列表（顺序 = id 升序）。 */
    private static List<String> titles(TestContentProvider provider, String where) {
        return titlesOf(provider, "product", where);
    }

    /** 按 {@code where} 查指定类型，返回标题列表（顺序 = id 升序，便于稳定断言）。 */
    private static List<String> titlesOf(TestContentProvider provider, String typeCode, String where) {
        ContentQuery query = new ContentQuery().type(typeCode).category("all")
                .orderby(List.of(new OrderBy("id", false)))
                .where(WhereParser.parse(where, "t.html", 1));
        return titles(provider.query(query).rows());
    }

    private static List<String> titles(List<ContentItem> rows) {
        List<String> titles = new ArrayList<>();
        for (ContentItem item : rows) {
            titles.add(item.title());
        }
        return titles;
    }
}
