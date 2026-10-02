package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.model.UrlPatternResolver;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全量页面计划（static-publish.md §7.2.1、§7.2.2、§7.4、§7.6、§8.2）。
 *
 * <p><b>计划生成是纯读</b>：不写库、不写盘、不改任何状态（§8.2）。因此"只算计划"就是预演，
 * 后台可以在动手前看见"本次将重写 N 个页面、删除 M 个文件"。
 *
 * <p><b>增量发布也生成全量计划</b>（§8.2 最后一条）：只有全量计划才看得见"本页应该变"与
 * "本页应该消失"两件事，差异只是"渲染阶段只处理差异集"。
 *
 * <p>页面类型的个数按 §7.2.1 的表逐行落地；关掉的页面类型**不出页也不报缺模板的错**
 * （§7.3 第（3）条），这是"小站不生成一堆空页面"的实现方式。
 */
public final class SitePlanner {

    /** 分类树展开的深度上限：分类是可递归的，但 8 层已远超实际建制。 */
    private static final int CATEGORY_DEPTH = 8;

    private final ContentProvider provider;
    private final ThemeTemplateLookup lookup;

    public SitePlanner(ContentProvider provider, ThemeTemplateLookup lookup) {
        this.provider = provider;
        this.lookup = lookup;
    }

    /** 一次全量计划的结果：页面 + 非页面产物（feed / 404 / 站点标识）的模板来源。 */
    public record Result(List<Plan> pages, List<String> warnings) {
    }

    public Result plan() {
        List<Plan> pages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        SiteConfig site = provider.site();

        addHome(pages, site);
        addTypeListPages(pages, warnings);
        addCategoryListPages(pages, site, warnings);
        addDetailPages(pages, warnings);
        addSinglePages(pages, warnings);
        addTagPages(pages, site, warnings);
        addArchivePages(pages, site, warnings);
        addFacetPages(pages, site, warnings);
        addStaticPages(pages, site, warnings);
        addSearchPage(pages, site, warnings);
        addNotFoundPage(pages, warnings);

        detectConflicts(pages);
        return new Result(List.copyOf(pages), List.copyOf(warnings));
    }

    /* ---------------- 1. HOME ---------------- */

    private void addHome(List<Plan> pages, SiteConfig site) {
        String template = find(PageType.HOME, null, null, null,
                "首页（PageType.HOME）");
        String pattern = site.option("url.home", "/page-{n}/");
        PageUrlBuilder urls = UrlPatternResolver.pageUrls(pattern, Map.of());
        // 首页的 `{cms:list}` 是分页主体，第 2..N 页的 URL 由站点选项 url.home 决定（§7.1.3）
        ContentQuery query = new ContentQuery().category("all").row(0);
        pages.add(new Plan(PageType.HOME, "/", "index.html", template, null,
                Plan.SourceRef.home(), 1, urls, null, query, Map.of("urls", urls),
                site.name(), PageType.HOME));
    }

    /* ---------------- 2. LIST（类型来源）与 3. LIST（分类来源） ---------------- */

    /**
     * 类型列表页：每个 {@code list_url_pattern} 非空且**有内容**的类型 1 个（§7.2.1 第 2 行）。
     *
     * <p>"有内容"用 {@code provider.count} 判，口径与列表查询一致（§6.3 的时间窗）——
     * 否则定时发布的内容会凭空多出一个空列表页。
     */
    private void addTypeListPages(List<Plan> pages, List<String> warnings) {
        for (ContentTypeDef type : provider.types()) {
            if (type.kind() == ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            // 没有 list_url_pattern 的类型不出列表页（§2.1："为空表示该类型不出列表页"）。
            // 层级类型（chapter）也在这里被挡掉——章节的入口是书的详情页，不是独立列表页。
            if (type.listUrlPattern() == null || type.listUrlPattern().isBlank()) {
                continue;
            }
            if (isAuthorType(type)) {
                // 作者页是作者这个「内容类型」的 DETAIL 页（§7.2.1 明写），不出类型列表页
                continue;
            }
            ContentQuery probe = new ContentQuery().type(type.code()).category("all").row(1);
            if (provider.count(probe) <= 0) {
                continue;
            }
            String template;
            try {
                template = find(PageType.LIST, type, type.code(), null,
                        "类型 " + type.code() + " 的列表页");
            } catch (PublishException e) {
                // 缺某个类型的列表模板不该阻断整站发布：记警告，跳过这一类页面（§7.3 第（2）条）
                warnings.add("跳过类型 " + type.code() + " 的列表页：" + e.getMessage());
                continue;
            }
            Map<String, Object> values = Map.of("typeCode", type.code());
            PageUrlBuilder urls = UrlPatternResolver.pageUrls(type.listUrlPattern(), values);
            // 该类型下的内容数：类型列表页的 channel 就是"这个类型"，count 是它的内容总数。
            // 计数与列表查询同一个判定函数（§6.3），因此定时未到点的内容不会被算进来。
            long typeCount = provider.count(new ContentQuery().type(type.code()).category("all").row(1));
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", urls);
            scope.put("channel", typeChannel(type, urls.firstPageUrl(), typeCount));
            pages.add(new Plan(PageType.LIST, urls.firstPageUrl(),
                    UrlPatternResolver.toArtifactPath(urls.firstPageUrl()), template, type.code(),
                    Plan.SourceRef.type(type.code()), 1, urls, null,
                    new ContentQuery().type(type.code()).category("all").row(type.perPageOrDefault()),
                    scope, type.name(), PageType.LIST));
        }
    }

    /**
     * 分类索引页：每个**有内容**的分类 1 个，URL 用站点选项 {@code url.list}（§7.2.1 第 2 行）。
     *
     * <p>分类索引页上的 {@code {cms:list}} 缺省 {@code category=当前分类}、{@code type='all'}
     * （该分类下所有类型的混合流，§7.2.1 v2.2 定死）——这正是"栏目"的语义。
     */
    private void addCategoryListPages(List<Plan> pages, SiteConfig site, List<String> warnings) {
        if (!site.flag("page.category", true)) {
            return;
        }
        String pattern = site.option("url.list", "/{categoryPath}/page-{n}/");
        for (NavItem category : allCategories()) {
            Map<String, Object> values = new LinkedHashMap<>(category.values());
            values.put("categoryPath", pathOf(category));
            values.put("categorySlug", category.get("slug"));
            PageUrlBuilder urls;
            try {
                urls = UrlPatternResolver.pageUrls(pattern, values);
            } catch (UrlPatternResolver.IllegalPatternException e) {
                // 静默 continue 会让预演"凭缺少页面"而说不出原因（§7.3：失败要可诊断）
                warnings.add("跳过分类 " + category.label() + " 的索引页：" + e.getMessage());
                continue;
            }
            ContentQuery probe = new ContentQuery().type("all").category(String.valueOf(category.id()))
                    .row(1);
            if (provider.count(probe) <= 0) {
                continue;
            }
            String template;
            try {
                template = find(PageType.LIST, null, null, null,
                        "分类 " + category.label() + " 的索引页");
            } catch (PublishException e) {
                // 以前这里是 return：一个分类缺模板会让**后续所有**分类索引页静默消失。
                // 与 addTypeListPages 的策略对齐——记 warning、跳过这一个，继续其余的（§7.3 第（2）条）。
                warnings.add("跳过分类 " + category.label() + " 的索引页：" + e.getMessage());
                continue;
            }
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", urls);
            Map<String, Object> channel = new LinkedHashMap<>(category.values());
            channel.put("label", category.label());
            channel.put("path", pathOf(category));
            scope.put("channel", channel);
            pages.add(new Plan(PageType.LIST, urls.firstPageUrl(),
                    UrlPatternResolver.toArtifactPath(urls.firstPageUrl()), template, null,
                    Plan.SourceRef.category(category.id(), String.valueOf(category.get("slug"))), 1,
                    urls, null,
                    new ContentQuery().type("all").category(String.valueOf(category.id())).row(0),
                    scope, String.valueOf(category.label()), PageType.LIST));
        }
    }

    /* ---------------- 4. DETAIL / DPAGE / SINGLE ---------------- */

    private void addDetailPages(List<Plan> pages, List<String> warnings) {
        boolean authorPages = provider.site().flag("page.author", true);
        for (ContentTypeDef type : provider.types()) {
            if (type.kind() == ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            if (isAuthorType(type) && !authorPages) {
                // §2.7 page.author=0：作者页（就是 author 类型的 DETAIL 页）整类不出
                continue;
            }
            if (type.detailUrlPattern() == null || type.detailUrlPattern().isBlank()) {
                continue;
            }
            String template;
            try {
                template = find(PageType.DETAIL, type, type.code(), null,
                        "类型 " + type.code() + " 的详情页");
            } catch (PublishException e) {
                warnings.add("跳过类型 " + type.code() + " 的详情页：" + e.getMessage());
                continue;
            }
            ContentQuery query = new ContentQuery().type(type.code()).category("all").row(0);
            for (ContentItem item : provider.query(query).rows()) {
                Plan plan = detailPlan(type, item, template, warnings);
                if (plan != null) {
                    pages.add(plan);
                }
            }
        }
    }

    /** 一条内容的详情页（含正文分页的第 2..N 页计划所需的 URL 规则与上下文）。 */
    private Plan detailPlan(ContentTypeDef type, ContentItem item, String template,
                            List<String> warnings) {
        Map<String, Object> values = urlValuesOf(item, type);
        String url;
        try {
            url = UrlPatternResolver.resolve(type.detailUrlPattern(), values);
        } catch (UrlPatternResolver.IllegalPatternException e) {
            warnings.add("跳过内容 #" + item.id() + " 的详情页：" + e.getMessage());
            return null;
        }
        if (url.isBlank()) {
            warnings.add("跳过内容 #" + item.id() + "：URL 规则算不出路径");
            return null;
        }
        PageUrlBuilder urls = UrlPatternResolver.pageUrls(type.detailUrlPattern(), values);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("urls", urls);
        scope.put("contentIds", List.of(item.id()));
        Map<String, Object> channel = primaryCategoryChannel(item);
        if (!channel.isEmpty()) {
            scope.put("channel", channel);
        }
        // 依赖的邻接内容（§8.4）：上一篇 / 下一篇的链接必须让本页重渲染
        ContentProvider.Neighbors neighbors =
                provider.neighbors(type.code(), item.id(), "type", "all");
        List<Long> neighborIds = new ArrayList<>();
        if (neighbors.prev() != null) {
            neighborIds.add(neighbors.prev().id());
        }
        if (neighbors.next() != null) {
            neighborIds.add(neighbors.next().id());
        }
        scope.put("neighborIds", neighborIds);
        String title = item.title() == null ? type.name() : item.title();
        return new Plan(PageType.DETAIL, url, UrlPatternResolver.toArtifactPath(url), template,
                type.code(), Plan.SourceRef.content(item.id(), type.code()), 1, urls, item, null,
                scope, title, PageType.DETAIL);
    }

    /**
     * 单页：每个 {@code SINGLE} 类型 1 个（§7.2.1 第 8 行）。
     *
     * <p>单页的 URL 一般写死（如 {@code /about/}），因此 {@code {slug}} 缺值也不会出事；
     * 但类型定义里若写了 <em>non-page</em> 形态（如 {@code /p/{slug}.html}），就会按内容算。
     */
    private void addSinglePages(List<Plan> pages, List<String> warnings) {
        for (ContentTypeDef type : provider.types()) {
            if (type.kind() != ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            if (type.detailUrlPattern() == null || type.detailUrlPattern().isBlank()) {
                continue;
            }
            String template;
            try {
                template = find(PageType.SINGLE, type, type.code(), null,
                        "单页 " + type.name());
            } catch (PublishException e) {
                warnings.add("跳过单页 " + type.code() + "：" + e.getMessage());
                continue;
            }
            ContentItem item = provider.query(new ContentQuery().type(type.code())
                    .category("all").row(1)).rows().stream().findFirst().orElse(null);
            if (item == null) {
                // 单页还没有内容时不出页：一个没有内容的"关于我们"没有意义
                continue;
            }
            Map<String, Object> values = urlValuesOf(item, type);
            String url;
            try {
                url = UrlPatternResolver.resolve(type.detailUrlPattern(), values);
            } catch (UrlPatternResolver.IllegalPatternException e) {
                warnings.add("跳过单页 " + type.code() + "：" + e.getMessage());
                continue;
            }
            if (url.isBlank()) {
                continue;
            }
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", PageUrlBuilder.single(url));
            scope.put("contentIds", List.of(item.id()));
            pages.add(new Plan(PageType.SINGLE, url, UrlPatternResolver.toArtifactPath(url), template,
                    type.code(), Plan.SourceRef.content(item.id(), type.code()), 1,
                    PageUrlBuilder.single(url), item, null, scope,
                    item.title() == null ? type.name() : item.title(), PageType.SINGLE));
        }
    }

    /* ---------------- 5. TAGLIST / TAGPAGE ---------------- */

    private void addTagPages(List<Plan> pages, SiteConfig site, List<String> warnings) {
        List<NavItem> tags = provider.tags(null, 0, "count", 1);
        if (site.flag("page.taglist", true)) {
            String tagsUrl = site.option("url.tags", "/tags/");
            String template = optional(PageType.TAGLIST, null, null, null);
            if (template != null && !tags.isEmpty()) {
                Map<String, Object> scope = new LinkedHashMap<>();
                scope.put("urls", PageUrlBuilder.single(tagsUrl));
                pages.add(new Plan(PageType.TAGLIST, tagsUrl,
                        UrlPatternResolver.toArtifactPath(tagsUrl), template, null,
                        Plan.SourceRef.special("taglist"), 1, PageUrlBuilder.single(tagsUrl), null,
                        null, scope, "标签总览", PageType.TAGLIST));
            }
        }
        if (!site.flag("page.tag", true)) {
            return;
        }
        int minCount = Math.max(1, site.number("page.tagMinCount", 1));
        String pattern = site.option("url.tag", "/tag/{tagSlug}/");
        String template = optional(PageType.TAGPAGE, null, null, null);
        if (template == null) {
            return;
        }
        for (NavItem tag : tags) {
            Object raw = tag.get("count");
            long count = raw instanceof Number number ? number.longValue() : 0L;
            if (count < minCount) {
                continue;
            }
            String slug = String.valueOf(tag.get("slug"));
            Map<String, Object> values = new LinkedHashMap<>(tag.values());
            values.put("tagSlug", slug);
            String url;
            PageUrlBuilder urls;
            try {
                url = UrlPatternResolver.resolve(pattern, values);
                urls = UrlPatternResolver.pageUrls(pattern, values);
            } catch (UrlPatternResolver.IllegalPatternException e) {
                warnings.add("跳过标签 " + tag.label() + " 的标签页：" + e.getMessage());
                continue;
            }
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", urls);
            Map<String, Object> channel = new LinkedHashMap<>(tag.values());
            channel.put("label", tag.label());
            scope.put("channel", channel);
            pages.add(new Plan(PageType.TAGPAGE, url, UrlPatternResolver.toArtifactPath(url), template,
                    null, Plan.SourceRef.tag(slug), 1, urls, null,
                    new ContentQuery().type("all").category("all").tags(List.of(slug)).row(0),
                    scope, String.valueOf(tag.label()), PageType.TAGPAGE));
        }
    }

    /* ---------------- 6. ARCHIVE ---------------- */

    private void addArchivePages(List<Plan> pages, SiteConfig site, List<String> warnings) {
        if (!site.flag("page.archive", false)) {
            return;
        }
        String pattern = site.option("url.archive", "/archive/{year}/{month}/");
        String mode = site.option("archive.mode", "month");
        String template;
        try {
            template = find(PageType.ARCHIVE, null, null, null, "归档页");
        } catch (PublishException e) {
            warnings.add("未出归档页：" + e.getMessage());
            return;
        }
        for (NavItem archive : provider.archives("all", mode, 0, "all", true)) {
            Map<String, Object> values = new LinkedHashMap<>(archive.values());
            values.put("year", archive.get("year"));
            values.put("month", archive.get("month"));
            String url;
            PageUrlBuilder urls;
            try {
                url = UrlPatternResolver.resolve(pattern, values);
                urls = UrlPatternResolver.pageUrls(pattern, values);
            } catch (UrlPatternResolver.IllegalPatternException e) {
                warnings.add("跳过归档 " + values.get("year") + "/" + values.get("month")
                        + " 的归档页：" + e.getMessage());
                continue;
            }
            if (url.isBlank()) {
                continue;
            }
            String label = String.valueOf(archive.get("label") == null ? url : archive.get("label"));
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", urls);
            scope.put("channel", archiveChannel(archive, label, url));
            pages.add(new Plan(PageType.ARCHIVE, url, UrlPatternResolver.toArtifactPath(url), template,
                    null, Plan.SourceRef.archive(label), 1, urls, null,
                    new ContentQuery().type("all").category("all").row(0), scope, label, PageType.ARCHIVE));
        }
    }

    /* ---------------- 7. FACET ---------------- */

    /**
     * 筛选落地页（§7.4）：只出**单字段**落地页与 {@code facets.combos} 里**显式声明**的交叉页，
     * 不做全排列——3 个字段 × 各 10 值 = 1000 个组合页，绝大多数是空页，还会让 GC 与增量失控。
     */
    private void addFacetPages(List<Plan> pages, SiteConfig site, List<String> warnings) {
        if (!site.flag("page.facet", false)) {
            return;
        }
        int maxPages = site.number("facets.maxPages", 500);
        List<Plan> facetPlans = new ArrayList<>();
        for (ContentTypeDef type : provider.types()) {
            if (type.kind() == ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            for (FacetConfig facet : facetsOf(type, site, warnings)) {
                List<NavItem> values = provider.facetValues(type.code(), facet.field());
                String template;
                try {
                    template = find(PageType.FACET, type, type.code(), null,
                            "类型 " + type.code() + " 的筛选页 " + facet.field());
                } catch (PublishException e) {
                    warnings.add("跳过筛选页 " + type.code() + "/" + facet.field() + "：" + e.getMessage());
                    // continue 而不是 break：模板缺失只影响当前筛选字段，不该把同一类型后面
                    // 其他字段的筛选页一起丢掉（与其它页型的策略一致）。
                    continue;
                }
                for (NavItem value : values) {
                    String path = String.valueOf(value.get("facetPath") == null
                            ? facet.field() + "-" + value.get("slug") : value.get("facetPath"));
                    if (!facet.paths().contains(path)) {
                        continue;
                    }
                    String url;
                    PageUrlBuilder urls;
                    try {
                        Map<String, Object> vars = Map.of("facetPath", path);
                        url = UrlPatternResolver.resolve(site.option("url.facet", "/f/{facetPath}/"), vars);
                        urls = UrlPatternResolver.pageUrls(
                                site.option("url.facet", "/f/{facetPath}/"), vars);
                    } catch (UrlPatternResolver.IllegalPatternException e) {
                        continue;
                    }
                    Map<String, Object> scope = new LinkedHashMap<>();
                    scope.put("urls", urls);
                    scope.put("channel", facetChannel(facet, value, url));
                    scope.put("facet", Map.of("field", facet.field(), "value",
                            String.valueOf(value.get("value"))));
                    facetPlans.add(new Plan(PageType.FACET, url,
                            UrlPatternResolver.toArtifactPath(url), template, type.code(),
                            Plan.SourceRef.facet(path), 1, urls, null,
                            new ContentQuery().type(type.code()).category("all").row(0), scope,
                            facet.label() + "：" + value.get("label"), PageType.FACET));
                }
            }
        }
        if (facetPlans.size() > maxPages) {
            throw PublishException.error(PublishErrorCode.E4005,
                    "筛选页数量超限：" + facetPlans.size() + " > " + maxPages,
                    null, 0,
                    "本站的 facets 配置会产出 " + facetPlans.size() + " 个筛选页",
                    "收窄类型选项里的 facets 配置，或提高站点选项 facets.maxPages（§7.4 第（4）条）");
        }
        pages.addAll(facetPlans);
    }

    /** 一个类型的筛选配置：单字段落地页 + 显式声明的交叉页。 */
    private record FacetConfig(String field, String label, Set<String> paths) {
    }

    @SuppressWarnings("unchecked")
    private List<FacetConfig> facetsOf(ContentTypeDef type, SiteConfig site, List<String> warnings) {
        Object raw = type.options().get("facets");
        if (!(raw instanceof List<?> list)) {
            if (raw instanceof CharSequence text && !text.toString().isBlank()) {
                warnings.add("类型 " + type.code() + " 的 facets 配置读不出来（期望是数组）：" + text);
            }
            return List.of();
        }
        List<String> combos = stringList(site.options().get("facets.combos"));
        List<FacetConfig> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> facet = (Map<String, Object>) map;
            String field = String.valueOf(facet.get("field"));
            if (field == null || "null".equals(field) || field.isBlank()) {
                continue;
            }
            boolean page = facet.get("page") instanceof Boolean flag ? flag
                    : !"0".equals(String.valueOf(facet.get("page"))) && !"false".equalsIgnoreCase(
                            String.valueOf(facet.get("page")));
            if (!page) {
                continue;
            }
            String label = facet.get("label") == null ? field : String.valueOf(facet.get("label"));
            Set<String> paths = new LinkedHashSet<>();
            for (NavItem value : provider.facetValues(type.code(), field)) {
                String path = String.valueOf(value.get("facetPath") == null
                        ? field + "-" + value.get("slug") : value.get("facetPath"));
                paths.add(path);
                // 交叉页：只有 facets.combos 里显式写过的组合才出页（§7.4 的第一个死限）
                for (String combo : combos) {
                    if (combo.equals(path) || combo.startsWith(path + "+") || combo.endsWith("+" + path)) {
                        paths.add(combo);
                    }
                }
            }
            result.add(new FacetConfig(field, label, Set.copyOf(paths)));
        }
        return result;
    }

    /* ---------------- 8. STATIC / SEARCH / 404 ---------------- */

    private void addStaticPages(List<Plan> pages, SiteConfig site, List<String> warnings) {
        for (Map<String, Object> entry : staticPages(site)) {
            String code = string(entry.get("code"));
            String url = string(entry.get("url"));
            String templateName = string(entry.get("template"));
            String declaredType = string(entry.get("type"));
            if (code == null || url == null || templateName == null) {
                warnings.add("pages.static 的条目缺少 code / url / template，已跳过：" + entry);
                continue;
            }
            // 站点选项里的模板名本来就是相对主题根的路径（§7.3 第（2）条 STATIC 行）
            if (!lookup.source().exists(templateName)) {
                warnings.add("跳过静态页 " + code + "：主题里没有 " + templateName);
                continue;
            }
            boolean listPage = "list".equals(declaredType);
            Map<String, Object> queryConfig = mapValue(entry.get("query"));
            ContentQuery listQuery = listPage ? staticQuery(queryConfig) : null;
            PageUrlBuilder urls = listPage
                    ? UrlPatternResolver.pageUrls(url, Map.of())
                    : PageUrlBuilder.single(url);
            String first = urls.firstPageUrl();
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("urls", urls);
            if (listQuery != null) {
                scope.put("staticQuery", listQuery);
            }
            // §7.2.3：STATIC 页也注入四个 param key，用于表单回显（form_error / form_ok）
            scope.put("param", Map.of("form_error", "", "form_ok", ""));
            // 站点声明的静态页默认按"普通页面"收录；错误页（50x.html）这类不该进 sitemap 的页面
            // 在条目里写 noindex=1 —— 页面类型层面 STATIC 没有"要不要收录"的说法，只有站点知道。
            if (truthy(entry.get("noindex"))) {
                scope.put("noindex", Boolean.TRUE);
            }
            pages.add(new Plan(PageType.STATIC, first, UrlPatternResolver.toArtifactPath(first),
                    templateName, null, Plan.SourceRef.staticPage(code), 1, urls, null, listQuery,
                    scope, code, PageType.STATIC));
        }
    }

    /** 站点声明的列表页的查询（§7.2.1：「条目声明了 query 时恰 1 个」分页主体）。 */
    private ContentQuery staticQuery(Map<String, Object> config) {
        ContentQuery query = new ContentQuery().category("all");
        String type = string(config.get("type"));
        query.type(type == null ? "all" : type);
        Object row = config.get("row");
        query.row(row instanceof Number number ? number.intValue() : 10);
        Object offset = config.get("offset");
        if (offset instanceof Number number) {
            query.offset(number.intValue());
        }
        List<OrderBy> orders = OrderBy.parseList(string(config.get("orderby")));
        if (!orders.isEmpty()) {
            query.orderby(orders);
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> staticPages(SiteConfig site) {
        Object raw = site.options().get("pages.static");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    ((Map<String, Object>) map).forEach((key, value) -> entry.put(key, value));
                    result.add(entry);
                }
            }
            return result;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            return JsonSupport.parseObjectList(text.toString());
        }
        return List.of();
    }

    private void addSearchPage(List<Plan> pages, SiteConfig site, List<String> warnings) {
        if (!site.flag("page.search", true)) {
            return;
        }
        String url = site.option("url.search", "/search/");
        String template = optional(PageType.SEARCH, null, null, null);
        if (template == null) {
            warnings.add("未出搜索页：主题里没有 search.html");
            return;
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("urls", PageUrlBuilder.single(url));
        // §7.2.3：搜索页注入 q 与 page 两个 key（只用于回显，不参与任何查询）
        scope.put("param", Map.of("q", "", "page", ""));
        pages.add(new Plan(PageType.SEARCH, url, UrlPatternResolver.toArtifactPath(url), template,
                null, Plan.SourceRef.special("search"), 1, PageUrlBuilder.single(url), null, null,
                scope, "搜索", PageType.SEARCH));
    }

    private void addNotFoundPage(List<Plan> pages, List<String> warnings) {
        String template = optional(PageType.PAGE404, null, null, null);
        if (template == null) {
            warnings.add("未出 404 页：主题里没有 404.html");
            return;
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("urls", PageUrlBuilder.single("/404.html"));
        pages.add(new Plan(PageType.PAGE404, "/404.html", "404.html", template, null,
                Plan.SourceRef.special("404"), 1, PageUrlBuilder.single("/404.html"), null, null,
                scope, "页面不存在", PageType.PAGE404));
    }

    /* ---------------- 冲突检测（§7.1.3） ---------------- */

    /**
     * 全量计划生成后对**全部产物路径**做一次去重：两个不同来源映射到同一路径 → E4004。
     *
     * <p>这是**计划期**的检查，不是发布期的（宁可不发布，也不发布一个被覆盖的页面）。
     */
    private void detectConflicts(List<Plan> pages) {
        Map<String, Plan> byPath = new LinkedHashMap<>();
        for (Plan plan : pages) {
            Plan previous = byPath.putIfAbsent(plan.path(), plan);
            if (previous != null) {
                throw PublishException.error(PublishErrorCode.E4004, "URL 冲突：" + plan.path(),
                        null, 0,
                        previous.pageType() + "（来源 " + previous.source().describe() + "，URL "
                                + previous.url() + "）与 " + plan.pageType() + "（来源 "
                                + plan.source().describe() + "，URL " + plan.url() + "）会写到同一个产物文件",
                        "改掉其中一个的 slug / URL 规则；两者的 URL 规则见 §7.1.1");
            }
        }
    }

    /* ---------------- 取值助手 ---------------- */

    private String find(PageType pageType, ContentTypeDef typeDef, String typeCode, String explicit,
                        String where) {
        String template = lookup.find(pageType, typeDef, typeCode, explicit);
        if (template == null) {
            throw lookup.notFound(pageType, lookup.candidates(pageType, typeDef, typeCode, explicit),
                    where);
        }
        return template;
    }

    /** 允许缺失的模板（搜索页 / 标签总览 / 404 / feed 都有内置兜底）。 */
    private String optional(PageType pageType, ContentTypeDef typeDef, String typeCode,
                            String explicit) {
        return lookup.find(pageType, typeDef, typeCode, explicit);
    }

    /** 内容 → URL 取值表（{@link UrlPatternResolver} 的输入）。 */
    private Map<String, Object> urlValuesOf(ContentItem item, ContentTypeDef type) {
        Map<String, Object> values = new LinkedHashMap<>(item.values());
        values.put("typeCode", type.code());
        values.putIfAbsent("slug", item.slug());
        values.putIfAbsent("id", item.id());
        Object categoryPath = item.get("categoryPath");
        if (categoryPath != null) {
            values.put("categoryPath", categoryPath);
            String path = String.valueOf(categoryPath);
            int slash = path.lastIndexOf('/');
            values.put("categorySlug", slash < 0 ? path : path.substring(slash + 1));
        }
        // 层级内容：{parentSlug} 来自父内容
        if (item.parentId() > 0) {
            ContentItem parent = provider.contentById(item.parentId());
            if (parent != null) {
                values.put("parentSlug", parent.slug());
                values.put("parentTitle", parent.title());
            }
        }
        return values;
    }

    /** 主分类的 {@code channel} 作用域（详情页是"其主分类"，§5.1 第（4）条）。 */
    private Map<String, Object> primaryCategoryChannel(ContentItem item) {
        Object raw = item.get("categories");
        if (!(raw instanceof List<?> list)) {
            return Map.of();
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> category = new LinkedHashMap<>();
            map.forEach((key, value) -> category.put(String.valueOf(key), value));
            Object dimension = category.get("dimension");
            boolean primary = dimension == null || "primary".equals(String.valueOf(dimension));
            if (primary) {
                category.put("label", category.get("name"));
                return category;
            }
        }
        return Map.of();
    }

    private Map<String, Object> typeChannel(ContentTypeDef type, String url, long count) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", type.id());
        channel.put("name", type.name());
        channel.put("label", type.name());
        channel.put("slug", type.code());
        channel.put("typeCode", type.code());
        channel.put("url", url);
        channel.put("count", count);
        return channel;
    }

    /**
     * 归档页的 {@code channel} 作用域（§5.1 第（4）条表里 ARCHIVE 那一行：{@code year} / {@code month}
     * / {@code label} / {@code url} / {@code count}）。
     *
     * <p>{@code year} / {@code month} **直接取归档条目**（{@link NavItem}）而不是取 URL 取值表：
     * 两者同源，但条目是数据层给的第一手值，不再经过一层拷贝——模板里 {@code [field:channel.year/]}
     * 的报错（"channel 上没有 key year"）正是"拷贝链上某一环丢了"这类问题最常见的表现。
     */
    private Map<String, Object> archiveChannel(NavItem archive, String label, String url) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("year", archive.get("year"));
        channel.put("month", archive.get("month"));
        channel.put("label", label);
        channel.put("url", url);
        channel.put("count", archive.get("count"));
        return channel;
    }

    private Map<String, Object> facetChannel(FacetConfig facet, NavItem value, String url) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("label", facet.label() + "：" + (value.get("label") == null
                ? value.get("value") : value.get("label")));
        channel.put("slug", value.get("slug"));
        channel.put("url", url);
        channel.put("facetPath", value.get("facetPath"));
        channel.put("count", value.get("count"));
        channel.put("field", facet.field());
        return channel;
    }

    /** 全部分类（扁平，含层级），逐层展开以免深度不确定。 */
    private List<NavItem> allCategories() {
        List<NavItem> all = new ArrayList<>();
        List<NavItem> level = provider.categories(null, 1, ContentProvider.CountScope.tree);
        if (level.isEmpty()) {
            level = provider.categories(0L, 1, ContentProvider.CountScope.tree);
        }
        int depth = 0;
        List<NavItem> current = level;
        while (!current.isEmpty() && depth++ < CATEGORY_DEPTH) {
            all.addAll(current);
            List<NavItem> next = new ArrayList<>();
            for (NavItem item : current) {
                if (item.id() <= 0) {
                    continue;
                }
                next.addAll(provider.categories(item.id(), 1, ContentProvider.CountScope.tree));
            }
            current = next;
        }
        return all;
    }

    /** 分类的完整 slug 路径（多级用 {@code /} 连接）；优先用数据层给的 {@code path}。 */
    private String pathOf(NavItem category) {
        Object path = category.get("path");
        if (path != null && !String.valueOf(path).isBlank()) {
            String text = String.valueOf(path);
            return text.startsWith("/") ? text.substring(1) : text;
        }
        Object slug = category.get("slug");
        return slug == null ? "" : String.valueOf(slug);
    }

    private static boolean isAuthorType(ContentTypeDef type) {
        return "author".equals(type.code());
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object element : list) {
                String text = string(element);
                if (text != null) {
                    values.add(text);
                }
            }
            return values;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            List<String> values = new ArrayList<>();
            for (String element : text.toString().split(",")) {
                String trimmed = element.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }
        return List.of();
    }

    /**
     * 站点选项里的布尔值：JSON 解析过的是 {@code Boolean}，手工在库里写的是 {@code 1} / {@code true}。
     *
     * <p>缺省一律 {@code false}——像 {@code noindex} 这种"写了才算数"的开关，读不出来时应当
     * 保持原有行为（收录），而不是猜成 true。
     */
    private static boolean truthy(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            return "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)
                    || "yes".equalsIgnoreCase(trimmed);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            return JsonSupport.parseObject(text.toString());
        }
        return Map.of();
    }
}
