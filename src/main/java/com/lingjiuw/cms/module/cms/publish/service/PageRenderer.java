package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.DefVersion;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import java.util.List;
import java.util.Map;

/**
 * 渲染一个 {@link Plan}（static-publish.md §5.4、§8.1 第 ④ 步）。
 *
 * <p><b>两类派生页用同一个模板、同一份上下文，唯一差别是 {@code page.pageNo}</b>：
 * <ul>
 *   <li>{@code LIST} 的 {@code {cms:list}} 分页 → 列表页第 1..N 页；</li>
 *   <li>{@code DETAIL} 的 {@code {cms:detail}} 正文分页（{@code paginate_body}）→ 详情页第 1..N 页。</li>
 * </ul>
 * 两者都由 {@code page.pageNo} 驱动，因此这里只做一件事：**先把页号放进 {@code page} 作用域，
 * 再让渲染器的预解析去读它**（{@code AbstractQueryTag.prepare} 与 {@code DetailTag.prepare}
 * 都写着"页号必须在 fill() 之前读"）。放反了会让每一页都渲染第 1 页的内容——这正是 §5.4 要防的
 * 那类"位置相关行为"。
 *
 * <p>总页数只能**渲染完才知道**（分页主体在预解析里算出来写进 {@code page.totalPages}），
 * 所以派生页计划是"渲染第 1 页 → 读 totalPages → 再渲染第 2..N 页"。
 */
public final class PageRenderer {

    private final TemplateCompiler compiler;
    private final TemplateRenderer renderer;
    private final ContentProvider provider;
    private final ThemeTemplateLookup lookup;

    public PageRenderer(TemplateCompiler compiler, TemplateRenderer renderer, ContentProvider provider,
                        ThemeTemplateLookup lookup) {
        this.compiler = compiler;
        this.renderer = renderer;
        this.provider = provider;
        this.lookup = lookup;
    }

    /** 一次渲染的产物：HTML 文本 + 这一页自己的 {@code page} 作用域（总页数、noindex…）。 */
    public record Rendered(String html, Map<String, Object> pageScope, int totalPages) {
    }

    /**
     * 渲染一页。
     *
     * @param plan  页面计划项
     * @param pageNo 页号；{@code 1} = 主路径形态
     */
    public Rendered render(Plan plan, int pageNo) {
        TemplateAst ast = compile(plan);
        RenderContext ctx = context(plan, pageNo);

        StringBuilder out = new StringBuilder(16 * 1024);
        // 标签优先用"本页的出口"（见 CurrentProvider 的类注释：Spring 注入进来的是请求作用域代理，
        // 在发布线程池里一调用就抛 ScopeNotActiveException）。线程会被复用，用完必须清掉。
        com.lingjiuw.cms.module.cms.publish.template.tag.CurrentProvider.set(provider);
        try {
            renderer.render(ast, ctx, out);
        } finally {
            com.lingjiuw.cms.module.cms.publish.template.tag.CurrentProvider.clear();
        }

        // 总页数只可能由**分页主体的预解析**算出来（ListTag / DetailTag 都调 Pagination.fill），
        // 而默认 noindex 只取决于页面类型、站点选项与页号（第 2..N 页即 pageNo > 1），
        // 因此这里不需要拿 totalPages 再校正一次（§7.1.4）。
        int totalPages = totalPagesOf(ctx);
        applyPagePolicy(ctx, plan, pageNo);
        return new Rendered(out.toString(), snapshot(ctx), totalPages);
    }

    private static int totalPagesOf(RenderContext ctx) {
        Object value = ctx.pageVar("totalPages");
        int total = value instanceof Number number ? number.intValue() : 0;
        return Math.max(total, 1);
    }

    /**
     * 这一页会产出几个页面（派生页计划，§5.4 第 5 步）。
     *
     * <p>不渲染、只编译：模板里的分页主体在预解析阶段才算出 {@code totalPages}，而预解析需要
     * 一次完整的上下文——因此这里退一步，用"模板有没有分页主体"来判定"要不要去渲染第 1 页问
     * 总页数"。真正分几页由第 1 页渲染完读 {@code page.totalPages} 得到。
     */
    public boolean paginates(Plan plan) {
        TemplateAst ast = compile(plan);
        // 分页主体存在 + URL 规则本身能表达页号 → 才可能有第 2 页。
        // 少了后半句，详情页上的"不分页列表"会被误当成派生页来源。
        // 注意：**后半句在调用方**（SitePublishService.renderOne 判 plan.pageUrls() != null），
        // 本方法只回答"模板里有没有分页主体"。
        return ast.paginationBody() != null;
    }

    /** 编译这一页的模板（编译缓存由 {@link TemplateCompiler} 持有，派生页因此只编译一次）。 */
    private TemplateAst compile(Plan plan) {
        CompileContext compileContext = new CompileContext(provider.siteId(), plan.pageType(),
                plan.typeCode(), provider, defVersion());
        return compiler.compile(plan.template(), compileContext, new ValidationReport());
    }

    /**
     * {@code DefVersion} 是编译缓存 key 的一部分（§4.4）。契约要求"类型定义 / 字段定义 /
     * 站点配置三者任一保存即变化"，而库里没有这三张表的版本列，因此这里用**内容指纹**等价实现：
     * 只数个数是不够的——重命名字段、改字段类型 / formatter 而字段总数不变时计数完全一样，
     * 缓存会命中用旧定义编译出的 AST，产出与当前定义不符的页面。
     *
     * <p>引擎的缓存生命周期是**一个批次**：批次开始时冻结站点配置与类型定义（§8.1 第 ① 步），
     * provider 是同一份快照，因此指纹在一批发布内稳定，缓存照常命中。
     */
    private DefVersion defVersion() {
        StringBuilder types = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        for (ContentTypeDef def : provider.types()) {
            types.append(def.code()).append('|').append(def.kind()).append('|')
                    .append(def.hierarchical()).append('|').append(def.detailUrlPattern()).append('|')
                    .append(def.listUrlPattern()).append('|').append(def.detailTemplate()).append('|')
                    .append(def.listTemplate()).append('|').append(def.paginateBody()).append('|')
                    .append(def.sortField()).append('|').append(def.sortOrder()).append('|')
                    .append(def.perPage()).append('|').append(def.seoTitleField()).append('|')
                    .append(def.seoDescField()).append('|').append(sortedOptions(def.options()))
                    .append('\n');
            for (FieldDef field : def.fields()) {
                fields.append(field.typeCode()).append('|').append(field.code()).append('|')
                        .append(field.fieldType()).append('|').append(field.raw()).append('|')
                        .append(field.indexed()).append('|').append(field.searchable()).append('|')
                        .append(field.required()).append('|').append(field.defaultValue()).append('|')
                        .append(field.options()).append('|').append(field.formatter()).append('|')
                        .append(field.crossSite()).append('\n');
            }
        }
        return new DefVersion(fingerprint(types), fingerprint(fields), siteConfigFingerprint());
    }

    /**
     * 站点配置指纹：发布选项参与模板语义（{@code url.*} / {@code page.*} / {@code pager.labels}），
     * 因此**键与值**都要进指纹——只数选项个数的话，改这些值的时候指纹完全不变。
     */
    private long siteConfigFingerprint() {
        SiteConfig site = provider.site();
        StringBuilder text = new StringBuilder(site.code() == null ? "" : site.code());
        text.append('|').append(site.theme()).append('|').append(site.protocol()).append('|')
                .append(site.domain()).append('\n').append(sortedOptions(site.options()));
        return fingerprint(text);
    }

    /** 选项表的稳定文本形态：键排序后逐项拼接（Map 的迭代顺序不参与指纹）。 */
    private static String sortedOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String key : new java.util.TreeSet<>(options.keySet())) {
            text.append(key).append('=').append(options.get(key)).append(';');
        }
        return text.toString();
    }

    /** 64 位 FNV-1a：{@code String.hashCode()} 只有 32 位，做缓存 key 的碰撞概率太高。 */
    private static long fingerprint(CharSequence text) {
        long hash = 1125899906842597L;
        for (int i = 0; i < text.length(); i++) {
            hash = (hash ^ text.charAt(i)) * 1099511628211L;
        }
        return hash;
    }

    /** 建这一页的渲染上下文：栈底当前条目 + 具名作用域 + 页号（§5.1、§5.4）。 */
    private RenderContext context(Plan plan, int pageNo) {
        RenderContext ctx = RenderContext.forPage(plan.pageType(), plan.typeCode(), plan.entry());
        // 派生页（page-2/…）的 pageType 是 DPAGE，但它继承来源页的语义——
        // §7.2.1 的"分类索引页缺省"必须照样生效（见 QueryParams.inheritsListPage）。
        ctx.sourcePageType(plan.sourcePageType());
        ctx.pushNamed("site", siteScope());
        Map<String, Object> channel = plan.channel();
        if (!channel.isEmpty()) {
            ctx.pushNamed("channel", channel);
        }
        Map<String, Object> params = plan.params();
        if (!params.isEmpty()) {
            ctx.pushNamed("param", params);
        }
        // §5.1 第（2）条：page 作用域恒存在；页号必须在预解析之前落好
        ctx.putPage("pageNo", pageNo);
        ctx.putPage("url", plan.url());
        ctx.putPage("canonical", absolute(plan.url()));
        ctx.putPage("title", plan.title() == null ? "" : plan.title());
        // §5.6：**没有**分页主体的页面也要有确定取值（{@code totalPages=0} / {@code paginationKind=''}），
        // 模板据此判"这一页根本没有分页"。有分页主体时 ListTag / DetailTag 的 Pagination.fill()
        // 会覆盖它们（fill 只在字段为空时补 url / canonical）。
        ctx.putPage("totalPages", 0);
        ctx.putPage("totalCount", 0L);
        ctx.putPage("pageSize", 0);
        ctx.putPage("paginationKind", "");
        ctx.putPage("isFirst", pageNo <= 1);
        // 归档页的年月进 page 作用域：它是"这一页是什么"的属性（与 page.url / page.title 同类），
        // 页面计划把年月放在 channel 里（§5.1 第（4）条那张表的 ARCHIVE 行），这里派生成 page.year / page.month。
        // 模板读 page.year 还是 channel.year 都行——两处同源，不存在"两个值"的风险。
        Object year = channel.get("year");
        if (year != null) {
            ctx.putPage("year", year);
            ctx.putPage("month", channel.get("month"));
        }
        applyPagePolicy(ctx, plan, pageNo);
        ctx.pageUrls(plan.pageUrls());
        ctx.fieldDefLookup(this::fieldDef);
        return ctx;
    }

    /**
     * 把 §5.6 的"所有页面都有"那 6 个里的 {@code noindex} / {@code robots} / {@code lastmod}
     * 在**渲染之前**填好。
     *
     * <p>为什么必须前置：主题的 {@code _partials/header.html} 里普遍写着
     * {@code {cms:if field='page.noindex'}}，而字段解析"找不到就报错"（§5.1）——
     * 页面计划不填，**每一页**的 {@code <head>} 都会编译/渲染失败。这不是"模板写错了"，
     * 是引擎欠了它一个值。
     *
     * <p>为什么可以前置：默认 noindex 只取决于页面类型、站点选项与**页号**，这三者在渲染前都已知
     * （第 2..N 页的存在即 {@code pageNo > 1}，不需要 {@code totalPages}）。
     *
     * <p>不取 {@code page.title} / {@code url} / {@code canonical}：契约把它们交给页面计划
     * （见 {@code Pagination.fillInto} 的注释），这里只碰策略类字段。
     */
    private void applyPagePolicy(RenderContext ctx, Plan plan, int pageNo) {
        boolean noindex = isNoindex(plan, pageNo);
        ctx.putPage("noindex", noindex);
        ctx.putPage("robots", noindex ? "noindex,follow" : "index,follow");
        ctx.putPage("lastmod", lastModifiedOf(plan));
    }

    /**
     * 默认 noindex 的判定（static-publish.md §7.6 第 2 段，逐条落地）：
     * <ul>
     *   <li>{@code SEARCH} / {@code 404} 恒不收录（它们没有内容）；</li>
     *   <li>{@code FACET} 默认不收录，站点选项 {@code seo.facetIndex} 可开（§7.4 第（5）条）；</li>
     *   <li>{@code DPAGE} 的第 2..N 页默认不收录，站点选项 {@code seo.paginatedIndex} 可开（§7.1.4）；</li>
     *   <li>站点选项 {@code seo.noindexTypes} 列出的内容类型（章节页要不要收录是站点策略）；</li>
     *   <li>{@code FEED} 不是 HTML 页面，不进 sitemap（它也走这个判定，语义一致）。</li>
     * </ul>
     */
    private boolean isNoindex(Plan plan, int pageNo) {
        SiteConfig site = provider.site();
        // 页面计划可以直接定这一页收不收录（站点声明的静态页，例如 50x.html）：
        // 页面类型层面 STATIC 没有这个说法，只有声明它的站点知道。计划说了就以计划为准。
        Object declared = plan.scope().get("noindex");
        if (declared instanceof Boolean value) {
            return value;
        }
        return switch (plan.pageType()) {
            case SEARCH, PAGE404, FEED -> true;
            case FACET -> !site.flag("seo.facetIndex", false);
            case DPAGE, DETAIL -> (pageNo > 1 && !site.flag("seo.paginatedIndex", false))
                    || noindexType(site, plan.typeCode());
            default -> noindexType(site, plan.typeCode());
        };
    }

    /** {@code seo.noindexTypes} 里列出的类型（值可能是数组，也可能是逗号串）。 */
    private static boolean noindexType(SiteConfig site, String typeCode) {
        if (typeCode == null) {
            return false;
        }
        Object raw = site.options().get("seo.noindexTypes");
        if (raw instanceof java.util.Collection<?> collection) {
            for (Object element : collection) {
                if (typeCode.equals(String.valueOf(element).trim())) {
                    return true;
                }
            }
            return false;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            for (String element : text.toString().split(",")) {
                if (typeCode.equals(element.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code page.lastmod}：当前条目（或计划声明的依赖内容）的 {@code updateTime}，ISO 形态。 */
    private String lastModifiedOf(Plan plan) {
        if (plan.entry() != null) {
            Object value = plan.entry().get("updateTime");
            if (value instanceof java.time.LocalDateTime time) {
                return time.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Map<String, Object> siteScope() {
        return provider.site().toScope();
    }

    private FieldDef fieldDef(String typeCode, String fieldCode) {
        ContentTypeDef def = typeCode == null ? null : provider.type(typeCode);
        return def == null ? null : def.field(fieldCode);
    }

    /** 站点绝对地址（{@code site.url} + 路径）；域名没配时退回路径本身。 */
    public String absolute(String url) {
        String base = provider.site().url();
        if (base == null || base.isBlank()) {
            return url == null ? "" : url;
        }
        return base + (url == null ? "" : url);
    }

    private Map<String, Object> snapshot(RenderContext ctx) {
        Map<String, Object> scope = new java.util.LinkedHashMap<>();
        for (String key : com.lingjiuw.cms.module.cms.publish.model.BuiltinFields.PAGE_KEYS) {
            scope.put(key, ctx.pageVar(key));
        }
        return scope;
    }

    /** 页面类型 × 内容类型 → 候选模板（预演功能要在计划期就给出"用哪个模板"）。 */
    public List<String> candidatesOf(PageType pageType, String typeCode, String explicit) {
        ContentTypeDef def = typeCode == null ? null : provider.type(typeCode);
        return lookup.candidates(pageType, def, typeCode, explicit);
    }

    /** 把 {@link PublishException} 的成因收敛成一行，供批次报告使用。 */
    public static String describe(PublishException e) {
        return e == null ? "" : e.getMessage();
    }
}
