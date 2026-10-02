package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.DefVersion;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.BodyKind;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.TemplateSource;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 编译期校验测试的公用夹具（§12.3 第 1 条：表驱动用例"源码进、错误出"）。
 *
 * <p>三件事都在这里，测试类只写"触发模板 + 期望错误码 + 文案关键字"：
 * <ol>
 *   <li><b>内存 {@link TemplateSource}</b>：可以精确控制 {@code mtime} 与 {@code size}，
 *       因此能造出"同长度替换片段"这种只靠 sha256 才看得见的失效；</li>
 *   <li><b>内存 {@link ContentProvider}</b>：类型 / 字段 / 表单 / 菜单 / 分类 / 标签 / 作者，
 *       以及站点发布选项；</li>
 *   <li><b>假标签实现</b>：14 个标签的参数表与 §6.7 矩阵逐格照抄（真实现由别的代理写，
 *       但"矩阵由 TagHandler.maxCount 声明"这条契约必须被真实消费，所以这里要有那一行）。</li>
 * </ol>
 */
final class CompileFixture {

    /* ---------------- 内存模板源 ---------------- */

    private final Map<String, TemplateSource.Template> files = new LinkedHashMap<>();

    private final TemplateSource source = relativePath -> files.get(relativePath);

    /** 放一个模板：mtime 与 size 由内容推出。 */
    CompileFixture file(String path, String text) {
        return file(path, text, 1_000L, text.length());
    }

    /**
     * 放一个模板并**显式指定 mtime 与 size**：用来造"内容变了、mtime 与 size 都没变"的现场
     * （同长度替换），只有 {@code astVersion} 里的 sha256 能发现它（§4.4 v2.2 定死）。
     */
    CompileFixture file(String path, String text, long mtime, long size) {
        files.put(path, new TemplateSource.Template(path, text, mtime, size));
        return this;
    }

    /* ---------------- 定义与站点 ---------------- */

    private final Map<String, ContentTypeDef> types = new LinkedHashMap<>();
    private final Map<String, FormDef> forms = new LinkedHashMap<>();
    private final List<String> menuCodes = new ArrayList<>(List.of("main", "footer"));
    private final Set<String> categories = new java.util.LinkedHashSet<>(Set.of("news", "about"));
    private final Set<String> tags = new java.util.LinkedHashSet<>(Set.of("java", "cms"));
    private final Set<String> authors = new java.util.LinkedHashSet<>(Set.of("zhang"));
    private final Map<String, Object> options = new LinkedHashMap<>();

    private SiteConfig site = new SiteConfig(1L, "demo", "示例站", "example.com", "https",
            null, "zh-CN", null, null, null, null, null, null, "/sites/demo", null, null,
            "default", null, null, Map.of());

    private final ContentProvider provider = new ContentProvider() {
        @Override
        public long siteId() {
            return 1L;
        }

        @Override
        public SiteConfig site() {
            return site;
        }

        @Override
        public List<ContentTypeDef> types() {
            return List.copyOf(types.values());
        }

        @Override
        public ContentTypeDef type(String typeCode) {
            return types.get(typeCode);
        }

        @Override
        public FormDef form(String code) {
            return forms.get(code);
        }

        @Override
        public List<String> formCodes() {
            return List.copyOf(forms.keySet());
        }

        @Override
        public List<String> menuCodes() {
            return List.copyOf(menuCodes);
        }

        @Override
        public boolean categoryExists(String idOrSlug) {
            return categories.contains(idOrSlug);
        }

        @Override
        public boolean tagExists(String slug) {
            return tags.contains(slug);
        }

        @Override
        public boolean authorExists(String idOrSlug) {
            return authors.contains(idOrSlug);
        }

        @Override
        public ContentItem content(String typeCode, long id) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public ContentItem contentById(long id) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public ContentItem contentBySlug(String typeCode, String slug) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public QueryResult query(ContentQuery query) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public long count(ContentQuery query) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<ContentItem> children(long parentId, String typeCode, int depth) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<ContentItem> contentAncestors(long contentId) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> categories(Long parentId, int depth, CountScope countScope) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public NavItem category(String idOrSlug) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> categoryAncestors(long categoryId) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> typeNodes() {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> menu(String code) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> facetValues(String typeCode, String fieldCode) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> tags(String typeCode, int row, String orderby, int minCount) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public NavItem tag(String slug) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public List<NavItem> archives(String typeCode, String mode, int row, String category,
                                      boolean includeChildren) {
            throw new UnsupportedOperationException("编译期不用取数");
        }

        @Override
        public Neighbors neighbors(String typeCode, long anchorId, String within, String category) {
            throw new UnsupportedOperationException("编译期不用取数");
        }
    };

    /** 加一个内容类型（默认 CONTENT、非层级、无正文分页）。 */
    CompileFixture type(ContentTypeDef def) {
        types.put(def.code(), def);
        return this;
    }

    /** 加一个内容类型 + 它的自定义字段。 */
    CompileFixture type(String code, ContentTypeDef.Kind kind, FieldDef... fields) {
        return type(new ContentTypeDef(1L, code, code, kind, kind == ContentTypeDef.Kind.TREE,
                "/" + code + "/{slug}.html", "/" + code + "/", null, null, null,
                "publishTime", "desc", 20, null, null, List.of(fields), Map.of()));
    }

    CompileFixture form(String code) {
        forms.put(code, new FormDef(code, code, null, null, List.of()));
        return this;
    }

    CompileFixture option(String key, Object value) {
        options.put(key, value);
        site = new SiteConfig(site.id(), site.code(), site.name(), site.domain(), site.protocol(),
                site.logo(), site.lang(), site.description(), site.keywords(), site.seoDescription(),
                site.icp(), site.contactPhone(), site.contactEmail(), site.rootDir(),
                site.defaultCover(), site.ogImage(), site.theme(), site.statisticsCode(),
                site.mediaHost(), Map.copyOf(options));
        return this;
    }

    /* ---------------- 编译 ---------------- */

    private PageType pageType = PageType.DETAIL;
    private String typeCode = "product";
    private DefVersion defVersion = DefVersion.ZERO;

    CompileFixture page(PageType pageType, String typeCode) {
        this.pageType = pageType;
        this.typeCode = typeCode;
        return this;
    }

    CompileFixture defVersion(DefVersion defVersion) {
        this.defVersion = defVersion;
        return this;
    }

    CompileContext context() {
        return new CompileContext(1L, pageType, typeCode, provider, defVersion);
    }

    /** 全套校验器（§4.5 的 18 条 + §6.7 的矩阵），按 order() 执行。 */
    static List<TemplateValidator> allValidators(TagRegistry registry) {
        return List.of(
                new TagNameValidator(registry),
                new ParamValidator(registry),
                new StructureValidator(registry),
                new FieldPathValidator(registry),
                new FilterFieldValidator(registry),
                new ReferenceValidator(registry),
                new PaginationBodyValidator(registry),
                new FormCodeValidator(),
                new PaginationKindValidator(),
                new AnchorOfValidator(),
                new DetailTypeValidator(registry),
                new NamedQueryValidator(registry),
                new TagPageTypeMatrixValidator(registry),
                new DisabledPageTypeWarner());
    }

    TemplateCompiler compiler() {
        List<TagHandler> handlers = new ArrayList<>(FakeTags.all());
        handlers.addAll(extraTags);
        TagRegistry registry = new TagRegistry(handlers);
        return new TemplateCompiler(source, registry, allValidators(registry));
    }

    private final List<TagHandler> extraTags = new ArrayList<>();

    /**
     * 额外注册一个**自闭合**标签（14 个契约标签里只有 {@code include} 是自闭合的，而它在展开期
     * 就被消费掉了）——用来覆盖 §4.5 第 3 条"写法与声明不符"的另一半：
     * 自闭合标签被写成了块标签。
     */
    CompileFixture selfClosingTag(String name) {
        extraTags.add(new FakeTag(name, List.of(), false, BodyKind.NONE, false, false, Map.of()));
        return this;
    }

    /** 编译入口模板，返回 AST。 */
    TemplateAst compile(String path) {
        return compiler().compile(path, context(), new ValidationReport());
    }

    /** 编译并断言抛出 {@link PublishException}（单条错误的场景）。 */
    PublishException error(String path) {
        try {
            compile(path);
        } catch (PublishException e) {
            return e;
        }
        throw new AssertionError("期望编译报错，但编译通过了：" + path);
    }

    /** 断言错误码与文案关键字（§12.3 第 1 条要的三件套）。 */
    static void assertError(PublishException e, PublishErrorCode code, String... keywords) {
        org.junit.jupiter.api.Assertions.assertEquals(code, e.code(),
                "错误码不对：" + e.getMessage());
        for (String keyword : keywords) {
            org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains(keyword),
                    "文案里应该有「" + keyword + "」，实际是：\n" + e.getMessage());
        }
    }

    /**
     * 假标签实现：参数表照 §6.2–§6.5 抄，{@code maxCount} 照 §6.7 矩阵逐格抄。
     * 渲染方法不实现（编译期用不到）。
     */
    static final class FakeTags {

        private FakeTags() {
        }

        static List<TagHandler> all() {
            List<TagHandler> tags = new ArrayList<>();
            tags.add(tag("include", false, BodyKind.NONE, false, true, unlimited(),
                    ParamSpec.required("file", "片段路径")));
            tags.add(tag("if", true, BodyKind.NONE, false, false, unlimited(),
                    ParamSpec.required("field", "字段路径")));
            tags.add(tag("foreach", true, BodyKind.NONE, false, false, unlimited(),
                    ParamSpec.required("field", "多值字段"), ParamSpec.intOf("row", 0, "条数"),
                    ParamSpec.intOf("offset", 0, "跳过"), ParamSpec.of("orderby", "排序字段"),
                    ParamSpec.enumOf("order", "asc", "方向", "asc", "desc")));
            tags.add(query("list", BodyKind.LIST, true, Map.of(
                    PageType.SINGLE, 0, PageType.SEARCH, 0, PageType.PAGE404, 0, PageType.FEED, 0)));
            tags.add(query("query", BodyKind.NONE, true, Map.of()));
            tags.add(query("detail", BodyKind.DETAIL, true, Map.of(
                    PageType.HOME, 0, PageType.LIST, 0, PageType.TAGLIST, 0, PageType.TAGPAGE, 0,
                    PageType.ARCHIVE, 0, PageType.FACET, 0, PageType.SEARCH, 0, PageType.STATIC, 0,
                    PageType.PAGE404, 0, PageType.FEED, 0)));
            tags.add(tag("pagelist", true, BodyKind.NONE, false, false, allowed(Map.of(
                    PageType.SINGLE, 0, PageType.SEARCH, 0, PageType.PAGE404, 0, PageType.FEED, 0)),
                    ParamSpec.intOf("listsize", 5, "页号窗口"),
                    ParamSpec.of("items", "first,prev,pageno,next,last", "要输出的项")));
            tags.add(tag("channel", true, BodyKind.NONE, false, false, allowed(Map.of(PageType.FEED, 0)),
                    ParamSpec.enumOf("source", "category", "来源", "category", "content", "type",
                            "menu", "facet"),
                    ParamSpec.of("type", "类型"), ParamSpec.of("field", "facet 字段"),
                    ParamSpec.of("code", "main", "菜单 code"), ParamSpec.of("channel", "起始节点"),
                    ParamSpec.of("parent", "起始节点"), ParamSpec.intOf("depth", 1, "层数"),
                    ParamSpec.enumOf("countScope", "tree", "计数口径", "node", "tree"),
                    ParamSpec.intOf("row", 0, "条数")));
            tags.add(tag("breadcrumb", true, BodyKind.NONE, false, false, allowed(Map.of(
                    PageType.HOME, 0, PageType.SEARCH, 0, PageType.STATIC, 0, PageType.PAGE404, 0,
                    PageType.FEED, 0)),
                    ParamSpec.enumOf("from", "home", "起点", "home", "channel"),
                    ParamSpec.boolOf("withContent", 1, "含内容层级"),
                    ParamSpec.enumOf("contentLabel", "title", "内容层文案", "title", "categoryName")));
            tags.add(tag("prenext", true, BodyKind.NONE, false, false, only(PageType.DETAIL),
                    ParamSpec.enumOf("type", "both", "方向", "prev", "next", "both"),
                    ParamSpec.enumOf("within", "type", "范围", "type", "parent"),
                    ParamSpec.of("category", "all", "限定分类")));
            tags.add(tag("tagnav", true, BodyKind.NONE, false, false, allowed(Map.of(PageType.FEED, 0)),
                    ParamSpec.of("type", "类型"), ParamSpec.intOf("row", 50, "条数"),
                    ParamSpec.enumOf("orderby", "count", "排序", "count", "name", "sort"),
                    ParamSpec.intOf("minCount", 1, "最少内容数")));
            tags.add(tag("archive", true, BodyKind.NONE, false, false, allowed(Map.of(PageType.FEED, 0)),
                    ParamSpec.of("type", "类型"),
                    ParamSpec.enumOf("mode", "month", "粒度", "year", "month"),
                    ParamSpec.intOf("row", 24, "条数"), ParamSpec.of("category", "all", "限定栏目")));
            tags.add(tag("form", true, BodyKind.NONE, false, false, allowed(Map.of(PageType.FEED, 0)),
                    ParamSpec.required("code", "表单 code"), ParamSpec.of("class", "class"),
                    ParamSpec.of("hidden", "隐藏域"), ParamSpec.of("submitLabel", "按钮文案"),
                    ParamSpec.boolOf("ajax", 1, "允许 JS 拦截")));
            return tags;
        }

        private static TagHandler query(String name, BodyKind bodyKind, boolean queryTag,
                                        Map<PageType, Integer> maxCounts) {
            List<ParamSpec> params = new ArrayList<>();
            params.add(ParamSpec.of("type", "类型 code"));
            if (!"detail".equals(name)) {
                params.add(ParamSpec.of("category", "分类"));
                params.add(ParamSpec.boolOf("includeChildren", 1, "含子分类"));
                params.add(ParamSpec.of("tag", "标签 slug"));
                params.add(ParamSpec.of("author", "作者"));
                params.add(ParamSpec.enumOf("of", "self", "锚定项", "self", "parent"));
                params.add(ParamSpec.of("relate", "关联"));
                params.add(ParamSpec.enumOf("exclude", "self", "排除自身", "self"));
                params.add(ParamSpec.of("where", "字段过滤"));
                params.add(ParamSpec.of("keyword", "标题匹配"));
                params.add(ParamSpec.boolOf("top", 0, "仅置顶"));
                params.add(ParamSpec.boolOf("recommend", 0, "仅推荐"));
                params.add(ParamSpec.of("orderby", "排序"));
                params.add(ParamSpec.enumOf("order", "desc", "方向", "asc", "desc"));
                params.add(ParamSpec.intOf("row", 0, "条数"));
                params.add(ParamSpec.of("status", "PUBLISHED", "状态"));
            }
            if ("query".equals(name)) {
                params.add(ParamSpec.intOf("offset", 0, "跳过"));
                params.add(ParamSpec.intOf("depth", 1, "预加载层数"));
                params.add(ParamSpec.of("name", "查询名"));
                params.add(ParamSpec.intOf("cache", 0, "缓存秒数"));
            }
            return new FakeTag(name, params, true, bodyKind, queryTag, false, maxCounts);
        }

        private static TagHandler tag(String name, boolean needsBody, BodyKind bodyKind,
                                      boolean queryTag, boolean openParams,
                                      Map<PageType, Integer> maxCounts, ParamSpec... params) {
            return new FakeTag(name, List.of(params), needsBody, bodyKind, queryTag, openParams,
                    maxCounts);
        }

        private static Map<PageType, Integer> unlimited() {
            return Map.of();
        }

        private static Map<PageType, Integer> allowed(Map<PageType, Integer> zeroOn) {
            return zeroOn;
        }

        private static Map<PageType, Integer> only(PageType pageType) {
            Map<PageType, Integer> zeroOn = new LinkedHashMap<>();
            for (PageType value : PageType.values()) {
                if (value.matrixColumn() != pageType) {
                    zeroOn.put(value, 0);
                }
            }
            return zeroOn;
        }
    }

    /** 一个假标签：只声明契约里的那几件事，渲染不实现。 */
    private record FakeTag(String name, List<ParamSpec> params, boolean needsBody, BodyKind bodyKind,
                           boolean queryTag, boolean openParams,
                           Map<PageType, Integer> maxCounts) implements TagHandler {

        @Override
        public boolean preResolve() {
            return bodyKind != BodyKind.NONE || "query".equals(name);
        }

        @Override
        public int maxCount(PageType pageType) {
            Integer value = maxCounts.get(pageType);
            return value == null ? -1 : value;
        }

        @Override
        public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
            throw new UnsupportedOperationException("编译期不渲染");
        }
    }
}
