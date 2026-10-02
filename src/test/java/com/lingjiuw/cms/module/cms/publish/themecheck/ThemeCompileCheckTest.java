package com.lingjiuw.cms.module.cms.publish.themecheck;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.DefVersion;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateSource;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
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
import com.lingjiuw.cms.module.cms.publish.template.validate.NamedQueryValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationBodyValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.PaginationKindValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ParamValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.ReferenceValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.StructureValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.TagNameValidator;
import com.lingjiuw.cms.module.cms.publish.template.validate.TagPageTypeMatrixValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
 * mint 主题的**编译期验收**（static-publish.md §4.5 的 18 条校验）。
 *
 * <p>这个用例是主题作者的自检工具，不是引擎的用例：它按 §7.3 的模板查找规则，把
 * {@code sites/demo/template/mint} 下每一个模板**按它将来会被编译成的页面类型**编一遍
 * ——首页按 {@code HOME}、{@code article_list.html} 按 {@code LIST(article)}、
 * {@code chapter_detail.html} 按 {@code DETAIL(chapter)} 与 {@code DPAGE(chapter)}（派生页同一模板）、
 * {@code rank.html} 按「站点声明的列表页」的 {@code STATIC}……只要有一条编译错误，
 * 这个主题在真实站点上就会发布失败。
 *
 * <p>为什么必须"按页面类型"编译而不是随便编：§4.5 的第 4/5/7/8/13/14/15/17 条全都与
 * {@link CompileContext} 有关（该页面类型上有哪些具名作用域、有哪些字段、是不是分页主体、
 * 当前类型是不是层级类型），同一个 {@code detail.html} 在 {@code DETAIL} 下合法、
 * 在 {@code HOME} 下就是 E3012。这正是 §4.4 的"上下文签名"要解决的问题。
 *
 * <p>夹具照抄 {@code src/test/java/.../publish/TestContentProvider} 的建造者写法，
 * 内容模型与站点选项对齐 {@code 灵九演示站}（code=demo / theme=mint）。
 * 断言用的站点选项里 {@code pages.static} 必须列出 {@code rank.html} 且带 {@code query}
 * ——否则 §6.7 的 ※1 会把 {@code rank.html} 里的列表判成"静态页不许有列表"。
 */
@DisplayName("mint 主题：26 个模板的编译期验收")
class ThemeCompileCheckTest {

    private static final long SITE_ID = 1L;
    private static final Path THEME_ROOT = Paths.get("sites", "demo", "template", "mint");

    private TestContentProvider provider;
    private TemplateCompiler compiler;
    private DefaultTemplateRenderer renderer;

    /* ---------------- 夹具 ---------------- */

    @BeforeEach
    void setUp() {
        provider = demoProvider();

        TagRegistry registry = new TagRegistry(List.<TagHandler>of(
                new IncludeTag(), new IfTag(), new ElseTag(), new ForeachTag(),
                new ListTag(provider), new QueryTag(provider), new DetailTag(provider),
                new PagelistTag(provider), new ChannelTag(provider), new BreadcrumbTag(provider),
                new PrenextTag(provider), new TagnavTag(provider), new ArchiveTag(provider),
                new FormTag(provider)));

        // §4.5 的 18 条里凡是有实现类的全部挂上：多挂一条就多拦一类错误，不会误报。
        List<TemplateValidator> validators = List.of(
                new TagNameValidator(registry), new ParamValidator(registry),
                new StructureValidator(registry), new FieldPathValidator(registry),
                new FilterFieldValidator(registry), new ReferenceValidator(registry),
                new PaginationBodyValidator(registry), new PaginationKindValidator(),
                new DetailTypeValidator(registry), new AnchorOfValidator(),
                new FormCodeValidator(), new NamedQueryValidator(registry),
                new TagPageTypeMatrixValidator(registry));

        compiler = new TemplateCompiler(new ThemeSource(THEME_ROOT), registry, validators);
        renderer = new DefaultTemplateRenderer(registry);
    }

    /** 演示站的内容模型与发布选项（等价于另一路正在写的种子迁移）。 */
    private static TestContentProvider demoProvider() {
        SiteConfig site = new SiteConfig(SITE_ID, "demo", "灵九演示站", "demo.lingjiuw.local", "https",
                "/uploads/demo/logo.svg", "zh-CN", "薄荷主题演示站：清透的编辑风排版与全站静态化",
                "模板引擎,静态站,薄荷主题", "薄荷主题演示站", "京ICP备2026000001号",
                "010-8888-0000", "hi@lingjiuw.local", "sites/demo/www",
                "/uploads/demo/cover-default.svg", "/uploads/demo/og.png", "mint", "", "",
                Map.of());

        TestContentProvider.Builder builder = TestContentProvider.builder()
                .now(LocalDateTime.of(2026, 3, 31, 9, 0))
                .site(site)
                .siteOption("url.list", "/{categoryPath}/page-{n}/")
                .siteOption("url.tag", "/tag/{tagSlug}/")
                .siteOption("url.tags", "/tags/")
                .siteOption("url.archive", "/archive/{year}/{month}/")
                .siteOption("url.search", "/search/")
                .siteOption("url.facet", "/f/{facetPath}/")
                .siteOption("url.home", "/page-{n}/")
                .siteOption("page.archive", 1)
                .siteOption("page.tag", 1)
                .siteOption("page.taglist", 1)
                .siteOption("page.feed", 1)
                .siteOption("page.search", 1)
                .siteOption("page.facet", 1)
                .siteOption("facets.combos", List.of("brand-acme+region-huadong"))
                .siteOption("pager.labels", "首页,上一页,下一页,末页")
                .siteOption("pages.static", List.of(
                        staticPage("thanks", "/thanks/", "thanks.html", null),
                        staticPage("rank", "/rank/page-{n}/", "rank.html",
                                Map.of("type", "article", "orderby", "viewCountWeek desc", "row", 10))))
                .form(new FormDef("inquiry", "询价表单", "提交询价", "/thanks/", List.of(
                        new FormDef.FormField("name", "姓名", "text", true, "怎么称呼你", List.of()),
                        new FormDef.FormField("phone", "电话", "tel", true, "手机或座机", List.of()),
                        new FormDef.FormField("email", "邮箱", "email", false, "选填", List.of()),
                        new FormDef.FormField("message", "留言", "textarea", false, "想问什么", List.of()))))
                .itemUrl(item -> itemUrl(item.typeCode(), item.slug()))
                .category(1, 0, "news", "新闻")
                .category(2, 1, "tech", "科技")
                .category(3, 0, "case", "案例")
                .category(4, 3, "case-internal", "内部案例")
                .tag(1, "java", "Java")
                .tag(2, "template", "模板")
                .tag(3, "cms", "CMS")
                .tag(4, "static", "静态化")
                .tag(5, "design", "设计")
                .menu("main", List.of(
                        menuItem(1, "首页", "/", List.of()),
                        menuItem(2, "新闻", "/news/", List.of(menuItem(21, "科技", "/news/tech/", List.of()))),
                        menuItem(3, "产品", "/product/", List.of()),
                        menuItem(4, "书籍", "/book/", List.of()),
                        menuItem(5, "作者", "/author/", List.of()),
                        menuItem(6, "关于", "/about/", List.of()),
                        menuItem(7, "联系", "/contact/", List.of())));

        // ── 内容类型（§2.1）：detail_url_pattern / list_url_pattern / sort / per_page 照演示站声明 ──
        builder.type(type(10, "article", "文章", ContentTypeDef.Kind.CONTENT, false,
                "/{categoryPath}/{slug}.html", "/news/page-{n}/", null, "publishTime", "desc", 6,
                List.of(
                        indexed("article", "brand", FieldType.TEXT),
                        indexed("article", "price", FieldType.DECIMAL),
                        enumOf("article", "region", true, "huadong:华东", "huanan:华南", "huabei:华北"),
                        text("article", "sku", FieldType.TEXT),
                        text("article", "specs", FieldType.JSON),
                        text("article", "gallery", FieldType.IMAGES),
                        text("article", "download", FieldType.FILE),
                        indexed("article", "relatedProducts", FieldType.RELATION),
                        indexed("article", "weight", FieldType.INT),
                        indexed("article", "featured", FieldType.BOOL),
                        text("article", "tags", FieldType.TAGS),
                        enumMulti("article", "readingLevel", "beginner:入门", "advanced:进阶"))));

        builder.type(type(11, "product", "产品", ContentTypeDef.Kind.CONTENT, false,
                "/product/{slug}.html", "/product/page-{n}/", null, "publishTime", "desc", 6,
                List.of(
                        enumOf("product", "brand", true, "acme:Acme", "globex:Globex", "initech:Initech"),
                        indexed("product", "price", FieldType.DECIMAL),
                        enumOf("product", "region", true, "huadong:华东", "huanan:华南", "huabei:华北"),
                        indexed("product", "stock", FieldType.INT),
                        text("product", "specs", FieldType.JSON),
                        text("product", "gallery", FieldType.IMAGES),
                        text("product", "download", FieldType.FILE),
                        indexed("product", "relatedProducts", FieldType.RELATION),
                        indexed("product", "featured", FieldType.BOOL))));

        builder.type(type(12, "book", "书籍", ContentTypeDef.Kind.TREE, true,
                "/book/{slug}.html", "/book/page-{n}/", null, "publishTime", "desc", 12,
                List.of(
                        text("book", "author", FieldType.TEXT),
                        enumOf("book", "bookStatus", true, "serializing:连载中", "finished:已完结"),
                        text("book", "tags", FieldType.TAGS))));

        // chapter 是层级类型、走正文分页：paginate_body='content'（§5.6）
        builder.type(type(13, "chapter", "章节", ContentTypeDef.Kind.TREE, true,
                "/book/{parentSlug}/{slug}.html", null, "content", "sort", "asc", 20,
                List.of(text("chapter", "volume", FieldType.TEXT))));

        builder.type(type(14, "author", "作者", ContentTypeDef.Kind.CONTENT, false,
                "/author/{slug}.html", "/author/page-{n}/", null, "publishTime", "desc", 12,
                List.of(
                        text("author", "honor", FieldType.TEXT),
                        text("author", "bio", FieldType.TEXTAREA),
                        text("author", "avatar", FieldType.IMAGE))));

        // SINGLE 类型：about / contact + 内置的 single
        builder.type(type(15, "about", "关于我们", ContentTypeDef.Kind.SINGLE, false,
                "/about/", null, null, "publishTime", "desc", 20, List.of()));
        builder.type(type(16, "contact", "联系我们", ContentTypeDef.Kind.SINGLE, false,
                "/contact/", null, null, "publishTime", "desc", 20, List.of()));
        builder.type(type(17, "single", "单页", ContentTypeDef.Kind.SINGLE, false,
                "/{slug}/", null, null, "publishTime", "desc", 20, List.of()));

        // ── 内容（编译不读数据，但渲染冒烟与"参数指向的对象存在"要用） ──
        for (int i = 1; i <= 8; i++) {
            builder.content(TestContentProvider.ContentSpec.of(i, "article",
                            "post-" + i, "第 " + i + " 篇：薄荷主题示例")
                    .publishTime("2026-03-0" + ((i % 9) + 1) + "T10:00")
                    .summary("这是第 " + i + " 篇演示文章的摘要，用来验证卡片与列表的排版。")
                    .cover("/uploads/demo/cover-" + i + ".svg")
                    .viewCount(100L * i)
                    .viewCountWeek(20L * i)
                    .viewCountDay(5L * i)
                    .commentCount(i)
                    .top(i == 1)
                    .recommend(i <= 4)
                    .category(i % 2 == 0 ? "tech" : "news")
                    .tag(i % 2 == 0 ? "cms" : "template")
                    .tag("java")
                    .author("401", "沈青", "shen-qing")
                    .content("<h2>第一节</h2><p>正文第 " + i + " 段，用来验证 raw 输出与目录。</p>"
                            + "<h3>小节</h3><p>更多内容。</p>")
                    .field("region", i % 3 == 0 ? "huanan" : "huadong")
                    .field("brand", "LingJiu")
                    .field("price", 128.5 * i)
                    .field("sku", "SKU-" + i)
                    .field("weight", 300 + i)
                    .field("featured", i % 4 == 0)
                    .field("readingLevel", List.of(enumItem(i % 3 == 0 ? "advanced" : "beginner",
                            i % 3 == 0 ? "进阶" : "入门")))
                    .field("specs", Map.of("材质", "铝合金", "尺寸", "168×88×8mm"))
                    .field("toc", List.of(
                            heading(2, "第一节", "h2-1"),
                            heading(3, "小节", "h3-1")))
                    .field("gallery", List.of(
                            media("/uploads/demo/a" + i + ".svg", "示例图 A", 800, 600),
                            media("/uploads/demo/b" + i + ".svg", "示例图 B", 1200, 900)))
                    .field("download", "/uploads/demo/guide-" + i + ".pdf")
                    // RELATION 的真实语义是"目标类型的完整内容项"（§2.2），测试库这里直接给成型的数据
                    .field("relatedProducts", List.of(
                            related(102L, "薄荷系列产品 102", "/product/product-102.html",
                                    "product", "产品", "globex", "huanan"),
                            related(103L, "薄荷系列产品 103", "/product/product-103.html",
                                    "product", "产品", "initech", "huadong"))));
        }

        for (int i = 0; i < 3; i++) {
            long id = 101 + i;
            builder.content(TestContentProvider.ContentSpec.of(id, "product", "product-" + id,
                            "薄荷系列产品 " + id)
                    .publishTime("2026-02-1" + (i + 1) + "T09:30")
                    .summary("一款演示用的产品，含规格参数、图集、附件与关联产品。")
                    .cover("/uploads/demo/product-" + id + ".svg")
                    .viewCount(500L * (i + 1))
                    .viewCountWeek(60L * (i + 1))
                    .category("case")
                    .tag("design")
                    .content("<p>产品说明正文。</p>")
                    .field("brand", i == 0 ? "acme" : i == 1 ? "globex" : "initech")
                    .field("region", i == 0 ? "huadong" : "huanan")
                    .field("price", 1999.0 + i * 300)
                    .field("stock", 10 + i)
                    .field("featured", i == 0)
                    .field("specs", Map.of("功率", "1200W", "电压", "220V"))
                    .field("gallery", List.of(media("/uploads/demo/p" + id + ".svg", "产品图", 1024, 768)))
                    .field("download", "/uploads/demo/product-" + id + ".pdf")
                    .field("relatedProducts", List.of(
                            related(101L, "薄荷系列产品 101", "/product/product-101.html",
                                    "product", "产品", "acme", "huadong"))));
        }

        builder.content(TestContentProvider.ContentSpec.of(201, "book", "mint-book", "薄荷手册")
                .publishTime("2026-01-10T08:00")
                .summary("一本用于演示层级类型与章节树的示例书。")
                .cover("/uploads/demo/book-201.svg")
                .viewCount(900)
                .viewCountWeek(120)
                .category("case")
                .tag("template")
                .field("author", "沈青")
                .field("bookStatus", "serializing"));
        // 注意：books 这里**不写**自定义字段的值来充当内置列。曾经把这个字段叫 status，那是错的：
        // status 是 cms_content 的内置列（DRAFT/PUBLISHED/OFFLINE），同名的自定义字段会让
        // [field:status format='label'] 解析到内置列（TEXT）上，编译期直接报"label 不能用在 TEXT 上"。
        // 种子里已经把它改名为 bookStatus（见 V20261004090003__demo_book_status_field.sql），这里同步；
        // 取值也必须真的写进去——字段解析是"找不到就报错"（§5.1），声明了不等于有值。
        builder.content(TestContentProvider.ContentSpec.of(202, "book", "ink-book", "深墨笔记")
                .publishTime("2025-12-01T08:00")
                .summary("另一本示例书。")
                .viewCount(300)
                .viewCountWeek(40)
                .category("case")
                .field("author", "林澈")
                .field("bookStatus", "finished"));

        for (int i = 1; i <= 4; i++) {
            builder.content(TestContentProvider.ContentSpec.of(300 + i, "chapter", "chapter-" + i,
                            "第 " + i + " 章 · 薄荷的排版")
                    .publishTime("2026-01-1" + i + "T08:00")
                    .parent(201)
                    .sort(i)
                    .viewCount(90L * i)
                    .content("<p>第 " + i + " 章的正文。</p><!--cms:page--><p>第二页的正文。</p>")
                    .field("volume", i <= 2 ? "第一卷 排版" : "第二卷 标签"));
        }

        builder.content(TestContentProvider.ContentSpec.of(401, "author", "shen-qing", "沈青")
                .publishTime("2026-01-01T08:00")
                .summary("模板引擎作者。")
                .field("honor", "薄荷主题作者")
                .field("bio", "写了十年后端，最近在折腾静态化。")
                .field("avatar", "/uploads/demo/avatar-401.svg"));
        builder.content(TestContentProvider.ContentSpec.of(402, "author", "lin-che", "林澈")
                .publishTime("2026-01-02T08:00")
                .field("honor", "签约作者")
                .field("bio", "写小说，也写文档。")
                .field("avatar", "/uploads/demo/avatar-402.svg"));

        builder.content(TestContentProvider.ContentSpec.of(501, "about", "about", "关于我们")
                .publishTime("2026-01-05T08:00")
                .summary("灵九演示站是模板引擎的示例站。")
                .content("<p>我们用一套自研的标签语言，把站点整站静态化。</p>"));
        builder.content(TestContentProvider.ContentSpec.of(502, "contact", "contact", "联系我们")
                .publishTime("2026-01-05T08:00")
                .summary("询价、合作、找客服都走这里。")
                .content("<p>上海市徐汇区某某路 100 号 · 工作日 9:00–18:00</p>"));
        builder.content(TestContentProvider.ContentSpec.of(503, "single", "thanks-doc", "感谢页说明")
                .publishTime("2026-01-05T08:00")
                .content("<p>单页兜底模板的示例内容。</p>"));

        return builder.build();
    }

    private static Map<String, Object> staticPage(String code, String url, String template,
                                                  Map<String, Object> query) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", code);
        entry.put("url", url);
        entry.put("template", template);
        if (query != null) {
            entry.put("query", query);
        }
        return entry;
    }

    private static Map<String, Object> nav(long id, String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("name", label);
        item.put("url", url);
        return item;
    }

    /**
     * 一个菜单项。
     *
     * <p><b>每个菜单项都显式带 {@code children}</b>（叶子节点给空列表）：模板里的
     * {@code {cms:if field='children'}} / {@code {cms:foreach field='children'}} 是按
     * "空列表判假"（§3.7 裁定一）写的，而 {@code DbContentProvider.buildMenuLevel} 目前只在
     * 非空时才写这个 key —— 叶子节点因此会在渲染期报 E1004。这里按"数据层补齐 children"的
     * 口径造数据，主题模板不必为此写成两种形态。
     */
    private static NavItem menuItem(long id, String label, String url, List<NavItem> children) {
        Map<String, Object> values = nav(id, label, url);
        values.put("kind", "url");
        values.put("target", "");
        values.put("rel", "");
        values.put("children", children);
        return NavItem.of(values);
    }

    private static Map<String, Object> media(String url, String alt, int width, int height) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("url", url);
        value.put("alt", alt);
        value.put("title", alt);
        value.put("width", width);
        value.put("height", height);
        value.put("name", url.substring(url.lastIndexOf('/') + 1));
        value.put("size", 20480);
        value.put("mime", "image/svg+xml");
        value.put("ext", "svg");
        return value;
    }

    /** {@code ENUM_MULTI} 的迭代项（§2.2：每项给 {@code value} 与 {@code label}）。 */
    private static Map<String, Object> enumItem(String value, String label) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        return item;
    }

    /** 正文目录项（§5.2.5 的 {@code toc} 迭代项：level / text / id / url）。 */    private static Map<String, Object> heading(int level, String text, String id) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("level", level);
        value.put("text", text);
        value.put("id", id);
        value.put("url", "#" + id);
        return value;
    }

    /** RELATION 迭代项（§2.2：每项带目标类型的全部字段）。 */
    private static Map<String, Object> related(long id, String title, String url, String typeCode,
                                               String typeName, String brand, String region) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("title", title);
        value.put("slug", url.substring(url.lastIndexOf('/') + 1).replace(".html", ""));
        value.put("url", url);
        value.put("canonical", "https://demo.lingjiuw.local" + url);
        value.put("typeCode", typeCode);
        value.put("typeName", typeName);
        value.put("brand", brand);
        value.put("region", region);
        value.put("price", 1999.0);
        value.put("stock", 12L);
        value.put("viewCount", 500L);
        value.put("viewCountWeek", 60L);
        return value;
    }

    private static String itemUrl(String typeCode, String slug) {
        return switch (typeCode) {
            case "article" -> "/news/" + slug + ".html";
            case "product" -> "/product/" + slug + ".html";
            case "book" -> "/book/" + slug + ".html";
            case "chapter" -> "/book/mint-book/" + slug + ".html";
            case "author" -> "/author/" + slug + ".html";
            case "about" -> "/about/";
            case "contact" -> "/contact/";
            default -> "/" + slug + "/";
        };
    }

    private static ContentTypeDef type(long id, String code, String name, ContentTypeDef.Kind kind,
                                       boolean hierarchical, String detailPattern, String listPattern,
                                       String paginateBody, String sortField, String sortOrder,
                                       int perPage, List<FieldDef> fields) {
        return new ContentTypeDef(id, code, name, kind, hierarchical, detailPattern, listPattern,
                null, null, paginateBody, sortField, sortOrder, perPage, null, null, fields, Map.of());
    }

    private static FieldDef text(String type, String code, FieldType fieldType) {
        return FieldDef.builder(type, code, fieldType).build();
    }

    private static FieldDef indexed(String type, String code, FieldType fieldType) {
        return FieldDef.builder(type, code, fieldType).indexed(true).build();
    }

    private static FieldDef enumOf(String type, String code, boolean indexed, String... pairs) {
        return FieldDef.builder(type, code, FieldType.ENUM).indexed(indexed)
                .options(options(pairs)).build();
    }

    /** 多选枚举（§2.2）：可被 {@code {cms:foreach}} 迭代出 {@code value} / {@code label}。 */
    private static FieldDef enumMulti(String type, String code, String... pairs) {
        return FieldDef.builder(type, code, FieldType.ENUM_MULTI).indexed(true)
                .options(options(pairs)).build();
    }

    private static List<EnumOption> options(String... pairs) {
        List<EnumOption> options = new ArrayList<>();
        for (String pair : pairs) {
            int at = pair.indexOf(':');
            options.add(new EnumOption(pair.substring(0, at), pair.substring(at + 1)));
        }
        return options;
    }

    /* ---------------- 每个模板对应的页面类型（§7.3 的查找规则决定） ---------------- */

    private record Target(String template, PageType pageType, String typeCode) {

        @Override
        public String toString() {
            return template + "  ←  " + pageType + (typeCode == null ? "" : "(" + typeCode + ")");
        }
    }

    private static List<Target> targets() {
        return List.of(
                new Target("index.html", PageType.HOME, null),
                new Target("list.html", PageType.LIST, null),
                new Target("category_list.html", PageType.LIST, null),
                new Target("article_list.html", PageType.LIST, "article"),
                new Target("product_list.html", PageType.LIST, "product"),
                new Target("book_list.html", PageType.LIST, "book"),
                new Target("tag_list.html", PageType.TAGPAGE, null),
                new Target("tags.html", PageType.TAGLIST, null),
                new Target("archive_list.html", PageType.ARCHIVE, null),
                new Target("facet_list.html", PageType.FACET, "product"),
                new Target("detail.html", PageType.DETAIL, "article"),
                new Target("article_detail.html", PageType.DETAIL, "article"),
                new Target("product_detail.html", PageType.DETAIL, "product"),
                new Target("book_detail.html", PageType.DETAIL, "book"),
                new Target("chapter_detail.html", PageType.DETAIL, "chapter"),
                // DPAGE：详情页第 2..N 页用的是同一个模板、同一份上下文（§7.2.1）
                new Target("chapter_detail.html", PageType.DPAGE, "chapter"),
                new Target("author_detail.html", PageType.DETAIL, "author"),
                new Target("single.html", PageType.SINGLE, "single"),
                new Target("about.html", PageType.SINGLE, "about"),
                new Target("contact.html", PageType.SINGLE, "contact"),
                new Target("search.html", PageType.SEARCH, null),
                new Target("thanks.html", PageType.STATIC, null),
                new Target("rank.html", PageType.STATIC, null),
                new Target("404.html", PageType.PAGE404, null),
                // 非页面产物模板（§7.3 第（4）条）：robots.txt 直接用站点作用域，按 STATIC 上下文编译
                new Target("robots.txt", PageType.STATIC, null),
                new Target("feed.xml", PageType.FEED, null));
    }

    /* ---------------- ① 编译期验收（本用例的主断言） ---------------- */

    @Test
    @DisplayName("26 个模板 × 各自的页面类型：全部编译通过，一条 PublishException 都没有")
    void everyTemplateCompilesClean() {
        List<String> failures = new ArrayList<>();
        List<String> ok = new ArrayList<>();

        for (Target target : targets()) {
            try {
                CompileContext ctx = new CompileContext(SITE_ID, target.pageType(), target.typeCode(),
                        provider, DefVersion.ZERO);
                TemplateAst ast = compiler.compile(target.template(), ctx, new ValidationReport());
                ok.add(target + "  →  " + ast.nodes().size() + " 个顶层节点，分页主体="
                        + (ast.paginationBody() == null ? "无" : ast.paginationBody().name())
                        + "，命名查询=" + ast.namedQueries());
            } catch (BizException e) {
                failures.add("✗ " + target + "\n" + e.getMessage());
            } catch (RuntimeException e) {
                failures.add("✗ " + target + "\n" + e);
            }
        }

        System.out.println("========== mint 主题编译结果 ==========");
        ok.forEach(line -> System.out.println("✓ " + line));
        failures.forEach(System.out::println);
        System.out.println("通过 " + ok.size() + " / " + (ok.size() + failures.size()));

        assertThat(failures)
                .as("主题模板存在编译错误（下面是引擎给出的原始文案）%n%s", String.join("\n\n", failures))
                .isEmpty();
    }

    /* ---------------- ② 渲染冒烟：把页面真的渲染一遍，人眼看 HTML ---------------- */

    @Test
    @DisplayName("渲染冒烟：按页面类型装好上下文，逐页渲染并检查关键片段")
    void smokeRender() {
        StringBuilder report = new StringBuilder();

        String home = render("index.html", PageType.HOME, null, null, "/", 1, 3, null);
        assertThat(home).contains("<html lang=\"zh-CN\"").contains("全站最新").contains("热读排行")
                .contains("main-nav__link").contains("card--big").contains("pager__link")
                .contains("灵九演示站");

        String articleList = render("article_list.html", PageType.LIST, "article", null,
                "/news/page-2/", 2, 2, typeChannel("article", "文章", "/news/", 8));
        // 8 篇文章、每页 6 条 → 分页主体自己算出 2 页（本用例传进去的 totalPages 会被 fill 覆盖）
        assertThat(articleList).contains("第 2 / 2 页").contains("归档").contains("tagnav");

        String article = render("article_detail.html", PageType.DETAIL, "article",
                provider.content("article", 3), "/news/post-3.html", 1, 1,
                categoryChannel());
        assertThat(article).contains("<h1 class=\"article__title\">").contains("<h2>第一节</h2>")
                .doesNotContain("&lt;h2&gt;").contains("图集").contains("相关产品")
                .contains("资料下载").contains("toc__item");

        String product = render("product_detail.html", PageType.DETAIL, "product",
                provider.content("product", 101), "/product/product-101.html", 1, 1,
                categoryChannel());
        assertThat(product).contains("规格参数").contains("specs__key").contains("¥")
                .contains("本类热门对比").contains("compare");

        String book = render("book_detail.html", PageType.DETAIL, "book",
                provider.content("book", 201), "/book/mint-book.html", 1, 1, categoryChannel());
        assertThat(book).contains("章节目录").contains("catalog__item").contains("书库")
                .contains("最新章节");

        String chapter = render("chapter_detail.html", PageType.DETAIL, "chapter",
                provider.content("chapter", 301), "/book/mint-book/chapter-1.html", 1, 2,
                categoryChannel());
        assertThat(chapter).contains("《薄荷手册》目录").contains("content-pager")
                .contains("同书上下章").contains("path-line").doesNotContain("pager__link");

        String author = render("author_detail.html", PageType.DETAIL, "author",
                provider.content("author", 401), "/author/shen-qing.html", 1, 1, categoryChannel());
        assertThat(author).contains("author-box").contains("薄荷主题作者").contains("TA 的文章");

        String contact = render("contact.html", PageType.SINGLE, "contact",
                provider.content("contact", 502), "/contact/", 1, 1, null);
        assertThat(contact).contains("data-cms-form=\"inquiry\"")
                .contains("name=\"_hp\"").contains("data-cms-site=")
                .contains("name=\"contentId\"").contains("name=\"phone\"")
                .contains("提交询价");

        String search = render("search.html", PageType.SEARCH, null, null, "/search/", 1, 1, null);
        assertThat(search).contains("data-cms-search").contains("data-cms-search-result")
                .contains("value=\"薄荷\"");

        String tags = render("tags.html", PageType.TAGLIST, null, null, "/tags/", 1, 1, null);
        assertThat(tags).contains("tagcloud--wall").contains("data-count=").contains("按名称排列");

        String tagPage = render("tag_list.html", PageType.TAGPAGE, null, null, "/tag/cms/", 1, 2,
                tagChannel("cms", "CMS", 9));
        assertThat(tagPage).contains("tag_list.html").contains("#CMS");

        String archive = render("archive_list.html", PageType.ARCHIVE, null, null,
                "/archive/2026/03/", 1, 2, archiveChannel(2026, 3, 7));
        assertThat(archive).contains("按月归档").contains("feed-list--archive");

        String facet = render("facet_list.html", PageType.FACET, "product", null,
                "/f/brand-acme+region-huadong/", 1, 1, facetChannel("Acme · 华东",
                        "brand-acme+region-huadong", 4));
        assertThat(facet).contains("facet-chip").contains("noindex").contains("筛选落地页");

        String rank = render("rank.html", PageType.STATIC, null, null, "/rank/", 1, 1, null);
        // 8 篇文章、row=10 → 只有一页，pager 走"没有页码"的那一支
        assertThat(rank).contains("热读榜").contains("rank-list__no").contains("pager__info");

        String thanks = render("thanks.html", PageType.STATIC, null, null, "/thanks/", 1, 1, null);
        assertThat(thanks).contains("thanks__seal").contains("回执编号");

        String notFound = render("404.html", PageType.PAGE404, null, null, "/404.html", 1, 1, null);
        assertThat(notFound).contains("notfound__code").contains("404");

        String feed = render("feed.xml", PageType.FEED, null, null, "/feed.xml", 1, 1, null);
        assertThat(feed).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<rss version=\"2.0\"").contains("<item>").contains("<enclosure");

        String robots = render("robots.txt", PageType.STATIC, null, null, "/robots.txt", 1, 1, null);
        assertThat(robots).contains("Sitemap: https://demo.lingjiuw.local/sitemap.xml");

        String fallbackList = render("list.html", PageType.LIST, null, null, "/case/", 1, 1,
                categoryChannel());
        assertThat(fallbackList).contains("列表页兜底模板").contains("card--big");

        String categoryList = render("category_list.html", PageType.LIST, null, null, "/news/", 1, 1,
                categoryChannel());
        assertThat(categoryList).contains("栏目索引").contains("tree-card");

        String about = render("about.html", PageType.SINGLE, "about",
                provider.content("about", 501), "/about/", 1, 1, null);
        assertThat(about).contains("这个站是怎么搭起来的");

        String single = render("single.html", PageType.SINGLE, "single",
                provider.content("single", 503), "/thanks-doc/", 1, 1, null);
        assertThat(single).contains("单页兜底模板").contains("随便逛逛");

        report.append("\n========== 渲染冒烟：逐页产物大小 ==========\n");
        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            report.append(entry.getKey()).append("  →  ").append(entry.getValue().length()).append(" 字符\n");
        }
        System.out.println(report);
    }

    private final Map<String, String> rendered = new LinkedHashMap<>();

    private String render(String template, PageType pageType, String typeCode, ContentItem entry,
                          String url, int pageNo, int totalPages, Map<String, Object> channel) {
        CompileContext compileContext = new CompileContext(SITE_ID, pageType, typeCode, provider,
                DefVersion.ZERO);
        TemplateAst ast = compiler.compile(template, compileContext, new ValidationReport());

        RenderContext ctx = RenderContext.forPage(pageType, typeCode, entry);
        ctx.sourcePageType(pageType);
        ctx.pushNamed("site", provider.site().toScope());
        if (channel != null) {
            ctx.pushNamed("channel", channel);
        }
        ctx.putPage("url", url);
        ctx.putPage("title", url);
        ctx.putPage("canonical", provider.site().url() + url);
        ctx.putPage("noindex", pageType == PageType.FACET);
        ctx.putPage("robots", pageType == PageType.FACET ? "noindex,follow" : "index,follow");
        ctx.putPage("lastmod", "2026-03-31T09:00:00");
        // 归档页的年月由 PageRenderer 从 channel 派生成 page.year / page.month，这里同口径。
        if (channel != null && channel.get("year") != null) {
            ctx.putPage("year", channel.get("year"));
            ctx.putPage("month", channel.get("month"));
        }
        // 与 PageRenderer 同口径的 page 作用域初始值：**不含 pageType**（分页主体才写它），
        // 这样"在没有分页主体的页面上读 page.pageType"会被本用例当场抓出来。
        ctx.putPage("pageNo", pageNo);
        // 有分页主体的页面类型上给一个初始值（分页主体算完会覆盖它）；没有分页主体时是 0，
        // 与 PageRenderer 的初始值一致——模板据此判"这一页根本没有分页"（§5.6）。
        ctx.putPage("totalPages", pageType.paginatable() ? totalPages : 0);
        ctx.putPage("totalCount", 0L);
        ctx.putPage("pageSize", 0);
        ctx.putPage("paginationKind", "");
        ctx.putPage("isFirst", pageNo <= 1);
        ctx.pageUrls(new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return url;
            }

            @Override
            public String pageUrl(int page) {
                return page <= 1 ? url : url + "page-" + page + "/";
            }
        });
        ctx.fieldDefLookup(provider.fieldDefLookup());
        // §7.2.3 的注入 key 与页面计划同口径：SEARCH 给 q / page，STATIC 给 form_error / form_ok。
        // 其余页面类型**不注入**——这样"在 SINGLE 上读 param.*"会被本用例当场抓出来。
        if (pageType == PageType.SEARCH) {
            ctx.pushNamed("param", Map.of("q", "薄荷", "page", "1"));
        }
        if (pageType == PageType.STATIC) {
            ctx.pushNamed("param", Map.of("form_error", "", "form_ok", "IF-2026-0007"));
        }

        StringBuilder out = new StringBuilder();
        renderer.render(ast, ctx, out);
        String html = out.toString();
        rendered.put(template + "(" + pageType + (typeCode == null ? "" : "/" + typeCode) + ")",
                html);
        return html;
    }

    private static Map<String, Object> categoryChannel() {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", 1L);
        channel.put("name", "新闻");
        channel.put("label", "新闻");
        channel.put("slug", "news");
        channel.put("url", "/news/");
        channel.put("path", "/news");
        channel.put("count", 8L);
        return channel;
    }

    private static Map<String, Object> typeChannel(String typeCode, String label, String url, long count) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", 0L);
        channel.put("name", label);
        channel.put("label", label);
        channel.put("slug", typeCode);
        channel.put("url", url);
        channel.put("typeCode", typeCode);
        channel.put("count", count);
        return channel;
    }

    private static Map<String, Object> tagChannel(String slug, String label, long count) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", 3L);
        channel.put("name", label);
        channel.put("label", label);
        channel.put("slug", slug);
        channel.put("url", "/tag/" + slug + "/");
        channel.put("count", count);
        return channel;
    }

    private static Map<String, Object> archiveChannel(int year, int month, long count) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("year", year);
        channel.put("month", month);
        channel.put("label", year + " 年 " + month + " 月");
        channel.put("url", "/archive/" + year + "/" + String.format("%02d", month) + "/");
        channel.put("count", count);
        return channel;
    }

    private static Map<String, Object> facetChannel(String label, String facetPath, long count) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("label", label);
        channel.put("slug", facetPath);
        channel.put("url", "/f/" + facetPath + "/");
        channel.put("facetPath", facetPath);
        channel.put("count", count);
        return channel;
    }

    /**
     * 不经过 surefire 也能跑一遍（用来看完整的引擎报错文案）：
     * <pre>
     * mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
     * javac -encoding UTF-8 -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
     *       -d target/themecheck src/test/java/.../themecheck/ThemeCompileCheckTest.java
     * java -cp "target/classes;target/test-classes;target/themecheck;$(cat target/cp.txt)" \
     *       com.lingjiuw.cms.module.cms.publish.themecheck.ThemeCompileCheckTest
     * </pre>
     */
    public static void main(String[] args) {
        ThemeCompileCheckTest check = new ThemeCompileCheckTest();
        check.setUp();
        check.everyTemplateCompilesClean();
        check.smokeRender();
        System.out.println("\n✔ mint 主题：编译期验收与渲染冒烟全部通过");
    }

    /** 从主题目录读模板；片段按 {@link TemplateSource#fragmentPath} 落在 {@code _partials/} 下。 */
    static final class ThemeSource implements TemplateSource {

        private final Path root;

        ThemeSource(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public Template load(String relativePath) {
            Path file = root.resolve(relativePath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                return new Template(relativePath, source,
                        Files.getLastModifiedTime(file).toMillis(), Files.size(file));
            } catch (IOException e) {
                throw new IllegalStateException("读模板失败：" + relativePath, e);
            }
        }
    }
}
