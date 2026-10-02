package com.lingjiuw.cms.module.cms.publish;

import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.model.WhereCondition;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 内存版 {@link ContentProvider}（static-publish.md §2.6 期 1："标签实现只依赖 {@code ContentProvider}
 * 接口，因此引擎内核的测试可以用内存实现跑完"）。
 *
 * <p>**每个方法都有真实语义**：过滤、排序、分页、计数、层级、标签、归档、邻接全部在内存里的
 * {@code List} / {@code Map} 上算，没有空壳。引擎侧的两条承重口径在这里也只有一份实现：
 * <ul>
 *   <li>**一条判定函数**：{@code PUBLISHED ∧ publishTime ≤ now ∧ （expireTime 为空 或 &gt; now）}
 *       —— 列表、计数、标签数、归档数、分类数、邻接全部走 {@link #eligible}，
 *       所以"未到点的定时内容"既不会出现在列表里，也不会被计进任何计数（§6.3）；</li>
 *   <li>**排序末尾恒追加 {@code id desc} 兜底**（§6.3 v2.2 定死），见 {@link #comparator}。</li>
 * </ul>
 *
 * <p>造数据见 {@link #builder()}；{@link Builder} 上的方法刻意与 {@code ContentProvider} 的方法同名，
 * 读起来就是"往这个内存库里放什么"。
 */
public final class TestContentProvider implements ContentProvider {

    /* ---------------- 造数据的入口 ---------------- */

    /** 空库：站点 code {@code test}、域名 {@code example.com}、一个类型都没有。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code RenderContext.fieldDefLookup} 的现成实现（§2.2 的自定义字段声明）。
     * 端到端测试一行接上：{@code ctx.fieldDefLookup(provider.fieldDefLookup())}。
     */
    public RenderContext.FieldDefLookup fieldDefLookup() {
        return (typeCode, fieldCode) -> {
            if (typeCode == null || fieldCode == null) {
                return null;
            }
            ContentTypeDef def = type(typeCode);
            return def == null ? null : def.field(fieldCode);
        };
    }

    /* ---------------- 状态 ---------------- */

    private final SiteConfig site;
    private final Function<ContentItem, String> itemUrl;
    private final LocalDateTime now;

    private final Map<String, ContentTypeDef> types = new LinkedHashMap<>();
    private final Map<String, FormDef> forms = new LinkedHashMap<>();
    private final List<String> menuCodes = new ArrayList<>();
    private final Map<String, List<NavItem>> menus = new LinkedHashMap<>();

    /** 内容 id → 内容项；{@code LinkedHashMap} 让"同序插入"的测试可预期。 */
    private final Map<Long, ContentItem> contents = new LinkedHashMap<>();

    /** 分类 id → 分类项（{@code id} {@code name} {@code slug} {@code parentId} {@code url}）。 */
    private final Map<Long, NavItem> categories = new LinkedHashMap<>();
    private final Map<String, Long> categoryIdsBySlug = new LinkedHashMap<>();

    /** 标签 id → 标签项（{@code id} {@code name} {@code slug} {@code url}）。 */
    private final Map<Long, NavItem> tags = new LinkedHashMap<>();
    private final Map<String, Long> tagIdsBySlug = new LinkedHashMap<>();

    private TestContentProvider(Builder builder) {
        this.site = builder.site;
        this.itemUrl = builder.itemUrl;
        this.now = builder.now;
        this.types.putAll(builder.types);
        this.forms.putAll(builder.forms);
        this.menuCodes.addAll(builder.menuCodes);
        this.menus.putAll(builder.menus);
        this.categories.putAll(builder.categories);
        this.categoryIdsBySlug.putAll(builder.categoryIdsBySlug);
        this.tags.putAll(builder.tags);
        this.tagIdsBySlug.putAll(builder.tagIdsBySlug);
        // 先补齐"内容引用了但没显式注册"的分类 / 标签，再装配内容——否则那条内容会静默丢掉这个分类
        bootstrapNavNodes(builder.contents);
        for (ContentSpec spec : builder.contents) {
            ContentItem item = materialize(spec);
            if (contents.putIfAbsent(item.id(), item) != null) {
                throw new IllegalArgumentException("测试数据的 id 重复：" + item.id());
            }
        }
        finishDerivedFields();
    }

    /** 内容引用的分类 / 标签 slug 如果没注册过，就按 slug 自动建一个（名称 = slug）。 */
    private void bootstrapNavNodes(List<ContentSpec> specs) {
        for (ContentSpec spec : specs) {
            for (String slug : spec.categories) {
                if (resolveCategory(slug) == null) {
                    long id = 1;
                    while (categories.containsKey(id)) {
                        id++;
                    }
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("id", id);
                    values.put("parentId", 0L);
                    values.put("slug", slug);
                    values.put("name", slug);
                    values.put("label", slug);
                    values.put("url", "/" + slug + "/");
                    categories.put(id, NavItem.of(values));
                    categoryIdsBySlug.put(slug, id);
                }
            }
            for (String slug : spec.tags) {
                if (resolveTag(slug) != null) {
                    continue;
                }
                long id = 1;
                while (tags.containsKey(id)) {
                    id++;
                }
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("id", id);
                values.put("slug", slug);
                values.put("name", slug);
                values.put("label", slug);
                values.put("url", "/tag/" + slug + "/");
                tags.put(id, NavItem.of(values));
                tagIdsBySlug.put(slug, id);
            }
        }
    }

    /**
     * 第二遍：补那些**要看得到别人**才写得出的派生字段（{@code parentTitle} / {@code childCount} /
     * {@code hasChildren} / {@code ancestors}）。
     *
     * <p>为什么不能在 {@link #materialize} 里算：那时后面的内容还没进库，"子项在父项之后录入"
     * 是测试数据最常见的写法，一遍算出来的 {@code hasChildren} 会全是 {@code false}。
     */
    private void finishDerivedFields() {
        for (Map.Entry<Long, ContentItem> entry : contents.entrySet()) {
            Map<String, Object> values = entry.getValue().mutableValues();
            long id = entry.getKey();
            long parentId = entry.getValue().parentId();
            if (parentId > 0) {
                ContentItem parent = contents.get(parentId);
                if (parent != null) {
                    values.put("parentTitle", parent.title());
                    values.put("parentSlug", parent.slug());
                    values.put("parentUrl", parent.url());
                    values.put("parentTypeCode", parent.typeCode());
                }
            }
            List<ContentItem> children = children(id, null, 1);
            values.put("childCount", (long) children.size());
            values.put("hasChildren", !children.isEmpty());
            values.put("ancestors", List.copyOf(contentAncestors(id)));
            entry.setValue(ContentItem.of(values));
        }
    }

    /* ---------------- ContentProvider：站点与定义 ---------------- */

    @Override
    public long siteId() {
        return site.id();
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
        return typeCode == null ? null : types.get(typeCode);
    }

    @Override
    public FormDef form(String code) {
        return code == null ? null : forms.get(code);
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
        return resolveCategory(idOrSlug) != null;
    }

    @Override
    public boolean tagExists(String slug) {
        return resolveTag(slug) != null;
    }

    @Override
    public boolean authorExists(String idOrSlug) {
        if (idOrSlug == null) {
            return false;
        }
        for (ContentItem item : contents.values()) {
            if (Objects.equals(idOrSlug, text(item.get("authorId")))
                    || Objects.equals(idOrSlug, text(item.get("authorSlug")))) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- ContentProvider：单条内容 ---------------- */

    @Override
    public ContentItem content(String typeCode, long id) {
        ContentItem item = contents.get(id);
        return item != null && Objects.equals(typeCode, item.typeCode()) ? item : null;
    }

    @Override
    public ContentItem contentById(long id) {
        return contents.get(id);
    }

    @Override
    public ContentItem contentBySlug(String typeCode, String slug) {
        if (slug == null) {
            return null;
        }
        for (ContentItem item : contents.values()) {
            if (Objects.equals(typeCode, item.typeCode()) && slug.equals(item.slug())) {
                return item;
            }
        }
        return null;
    }

    /* ---------------- ContentProvider：列表查询 ---------------- */

    @Override
    public QueryResult query(ContentQuery query) {
        List<ContentItem> matched = sorted(filter(query), query);
        long total = matched.size();
        int pageSize = Math.max(query.row(), 0);
        if (pageSize == 0) {
            return new QueryResult(matched, total, 0);
        }
        int from = Math.min(Math.max(query.offset(), 0), matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return new QueryResult(matched.subList(from, to), total, pageSize);
    }

    @Override
    public long count(ContentQuery query) {
        // 与 query 用同一个 filter；这里是"计数与列表同口径"那一条的落点（§6.3）
        return filter(query).size();
    }

    @Override
    public List<ContentItem> children(long parentId, String typeCode, int depth) {
        List<ContentItem> result = new ArrayList<>();
        collectChildren(parentId, typeCode, Math.max(depth, 1), result);
        return result;
    }

    private void collectChildren(long parentId, String typeCode, int depth, List<ContentItem> out) {
        for (ContentItem item : contents.values()) {
            if (item.parentId() != parentId || !eligible(item)) {
                continue;
            }
            if (typeCode != null && !"all".equals(typeCode) && !Objects.equals(typeCode, item.typeCode())) {
                continue;
            }
            out.add(item);
            if (depth > 1) {
                collectChildren(item.id(), typeCode, depth - 1, out);
            }
        }
    }

    @Override
    public List<ContentItem> contentAncestors(long contentId) {
        List<ContentItem> chain = new ArrayList<>();
        ContentItem cursor = contents.get(contentId);
        Set<Long> seen = new LinkedHashSet<>();
        while (cursor != null && cursor.parentId() > 0 && seen.add(cursor.parentId())) {
            ContentItem parent = contents.get(cursor.parentId());
            if (parent == null) {
                break;
            }
            chain.add(parent);
            cursor = parent;
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    /* ---------------- ContentProvider：导航来源 ---------------- */

    @Override
    public List<NavItem> categories(Long parentId, int depth, CountScope countScope) {
        // 顶级分类在数据里是 parentId=0，而接口说"parentId 为 null 表示从顶级起"——两者归一
        Long start = parentId == null || parentId <= 0 ? 0L : parentId;
        Map<Long, List<NavItem>> byParent = new LinkedHashMap<>();
        for (NavItem category : categories.values()) {
            Long parent = parentIdOf(category);
            byParent.computeIfAbsent(parent == null ? 0L : parent, k -> new ArrayList<>()).add(category);
        }
        return buildCategoryLevel(byParent, start, Math.max(depth, 1), countScope);
    }

    private List<NavItem> buildCategoryLevel(Map<Long, List<NavItem>> byParent, Long parentId, int depth,
                                             CountScope countScope) {
        List<NavItem> level = byParent.getOrDefault(parentId, List.of());
        if (level.isEmpty()) {
            return List.of();
        }
        List<NavItem> result = new ArrayList<>(level.size());
        for (NavItem category : level) {
            long id = category.id();
            NavItem node = category.with("count", categoryCount(id, countScope));
            if (depth > 1) {
                List<NavItem> children = buildCategoryLevel(byParent, id, depth - 1, countScope);
                if (!children.isEmpty()) {
                    node = node.with("children", children);
                }
            }
            result.add(node);
        }
        return result;
    }

    @Override
    public NavItem category(String idOrSlug) {
        Long id = resolveCategory(idOrSlug);
        if (id == null) {
            return null;
        }
        // count 与 path 都是"现算"的：前者按判定函数，后者是面包屑要的顶级→自身路径（§6.3）
        return categories.get(id)
                .with("count", categoryCount(id, CountScope.node))
                .with("path", pathOf(id));
    }

    @Override
    public List<NavItem> categoryAncestors(long categoryId) {
        List<NavItem> chain = new ArrayList<>();
        Long cursor = categoryId;
        Set<Long> seen = new LinkedHashSet<>();
        while (cursor != null && seen.add(cursor)) {
            NavItem node = categories.get(cursor);
            if (node == null) {
                break;
            }
            chain.add(node);
            cursor = parentIdOf(node);
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    @Override
    public List<NavItem> typeNodes() {
        List<NavItem> nodes = new ArrayList<>();
        for (ContentTypeDef def : types.values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", def.id());
            values.put("label", def.name());
            values.put("name", def.name());
            values.put("slug", def.code());
            values.put("typeCode", def.code());
            values.put("url", def.listUrlPattern() == null ? "/" + def.code() + "/" : def.listUrlPattern());
            values.put("count", typeCount(def.code()));
            nodes.add(NavItem.of(values));
        }
        return nodes;
    }

    @Override
    public List<NavItem> menu(String code) {
        return List.copyOf(menus.getOrDefault(code, List.of()));
    }

    @Override
    public List<NavItem> facetValues(String typeCode, String fieldCode) {
        ContentTypeDef def = type(typeCode);
        FieldDef field = def == null ? null : def.field(fieldCode);
        if (field == null) {
            return List.of();
        }
        // 取值按字段定义里的 options 顺序（ENUM / ENUM_MULTI 是有限集合），没有选项时按首次出现顺序
        Map<String, String> labels = new LinkedHashMap<>();
        for (EnumOption option : field.options()) {
            labels.put(option.value(), option.label());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : labels.keySet()) {
            counts.put(value, 0);
        }
        for (ContentItem item : contents.values()) {
            if (!eligible(item) || !Objects.equals(typeCode, item.typeCode())) {
                continue;
            }
            for (Object value : valuesOf(item.get(fieldCode))) {
                counts.merge(text(value), 1, Integer::sum);
            }
        }
        List<NavItem> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("label", labels.getOrDefault(entry.getKey(), entry.getKey()));
            values.put("slug", entry.getKey());
            values.put("value", entry.getKey());
            values.put("url", "/" + typeCode + "/" + fieldCode + "-" + entry.getKey() + "/");
            values.put("facetPath", fieldCode + "-" + entry.getKey());
            values.put("count", entry.getValue());
            result.add(NavItem.of(values));
        }
        return result;
    }

    /* ---------------- ContentProvider：标签与归档 ---------------- */

    @Override
    public List<NavItem> tags(String typeCode, int row, String orderby, int minCount) {
        boolean allTypes = typeCode == null || "all".equals(typeCode);
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, NavItem> nodes = new LinkedHashMap<>();
        for (ContentItem item : contents.values()) {
            if (!eligible(item) || (!allTypes && !Objects.equals(typeCode, item.typeCode()))) {
                continue;
            }
            for (Object value : valuesOf(item.get("tagId"))) {
                Long id = resolveTag(text(value));
                if (id == null) {
                    continue;
                }
                counts.computeIfAbsent(text(value), k -> new long[1])[0]++;
                nodes.putIfAbsent(text(value), tags.get(id));
            }
        }
        List<NavItem> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            if (entry.getValue()[0] < minCount) {
                continue;
            }
            result.add(nodes.get(entry.getKey()).with("count", entry.getValue()[0]));
        }
        sortNav(result, orderby);
        return row > 0 && result.size() > row ? List.copyOf(result.subList(0, row)) : List.copyOf(result);
    }

    @Override
    public NavItem tag(String slug) {
        Long id = resolveTag(slug);
        if (id == null) {
            return null;
        }
        List<String> tokens = tagTokens(slug);
        long count = 0;
        for (ContentItem item : contents.values()) {
            if (eligible(item) && containsAny(item.get("tagId"), tokens)) {
                count++;
            }
        }
        return tags.get(id).with("count", count);
    }

    @Override
    public List<NavItem> archives(String typeCode, String mode, int row, String category,
                                  boolean includeChildren) {
        Set<Long> categoryIds = categoryFilter(category, includeChildren);
        Map<String, long[]> counts = new TreeMap<>(Comparator.reverseOrder());
        Map<String, LocalDateTime> newest = new LinkedHashMap<>();
        for (ContentItem item : contents.values()) {
            if (!eligible(item) || !matchesType(item, typeCode) || !matchesCategories(item, categoryIds)) {
                continue;
            }
            LocalDateTime publishTime = timeOf(item.get("publishTime"));
            if (publishTime == null) {
                continue;
            }
            String key = "year".equals(mode) ? String.valueOf(publishTime.getYear())
                    : publishTime.getYear() + "-" + String.format("%02d", publishTime.getMonthValue());
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            newest.merge(key, publishTime, (a, b) -> a.isAfter(b) ? a : b);
        }
        List<NavItem> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            String[] parts = entry.getKey().split("-");
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("year", Integer.parseInt(parts[0]));
            values.put("month", parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
            values.put("label", entry.getKey());
            values.put("url", "/archive/" + entry.getKey() + "/");
            values.put("count", entry.getValue()[0]);
            values.put("lastmod", newest.get(entry.getKey()).toString());
            result.add(NavItem.of(values));
        }
        return row > 0 && result.size() > row ? List.copyOf(result.subList(0, row)) : List.copyOf(result);
    }

    /* ---------------- ContentProvider：邻接 ---------------- */

    @Override
    public Neighbors neighbors(String typeCode, long anchorId, String within, String category) {
        ContentItem anchor = contents.get(anchorId);
        if (anchor == null) {
            return Neighbors.NONE;
        }
        ContentQuery query = new ContentQuery().type(typeCode).category(category)
                .orderby(defaultOrder(type(typeCode)));
        query.anchor(anchorId, anchor.parentId());
        List<ContentItem> peers = new ArrayList<>();
        for (ContentItem item : sorted(filter(query), query)) {
            // within='parent'：只在同一父内容的兄弟之间排（§6.4）
            if ("parent".equals(within) && item.parentId() != anchor.parentId()) {
                continue;
            }
            peers.add(item);
        }
        int index = -1;
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).id() == anchorId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return Neighbors.NONE;
        }
        // 排序是"默认顺序"，因此 prev = 序列里靠前的那条、next = 靠后的那条（§6.4）
        ContentItem prev = index > 0 ? peers.get(index - 1) : null;
        ContentItem next = index + 1 < peers.size() ? peers.get(index + 1) : null;
        return new Neighbors(prev, next);
    }

    /* ================= 语义实现 ================= */

    /**
     * 判定函数（§6.3 §6.4 唯一口径）：内容"现在可见"当且仅当
     * {@code PUBLISHED ∧ publishTime ≤ now ∧ （expireTime 为空 或 expireTime > now）}。
     */
    private boolean eligible(ContentItem item) {
        if (!"PUBLISHED".equals(text(item.get("status")))) {
            return false;
        }
        LocalDateTime publishTime = timeOf(item.get("publishTime"));
        if (publishTime != null && publishTime.isAfter(now)) {
            return false;
        }
        LocalDateTime expireTime = timeOf(item.get("expireTime"));
        return expireTime == null || expireTime.isAfter(now);
    }

    /** 一次查询的全部筛选条件；{@link #query} 与 {@link #count} 都走这里。 */
    private List<ContentItem> filter(ContentQuery query) {
        Set<Long> categoryIds = categoryFilter(query.category(), query.includeChildren());
        ContentItem anchor = query.anchorId() > 0 ? contents.get(query.anchorId()) : null;
        List<ContentItem> result = new ArrayList<>();
        for (ContentItem item : contents.values()) {
            if (query.excludeSelf() && item.id() == query.anchorId()) {
                continue;
            }
            if (!matchesStatus(item, query.status())) {
                continue;
            }
            if (!matchesType(item, query.typeCode())) {
                continue;
            }
            if (!matchesCategories(item, categoryIds)) {
                continue;
            }
            boolean matchedAllTags = true;
            for (String tag : query.tags()) {
                // §6.3：多值标签之间是 AND。值可以是 id 也可以是 slug（§2.5 规定值一律用 id，
                // 但 slug 更常见于手写模板，两种都认——真实现里靠 cms_tag 解析一次）
                if (!containsAny(item.get("tagId"), tagTokens(tag))) {
                    matchedAllTags = false;
                    break;
                }
            }
            if (!matchedAllTags) {
                continue;
            }
            if (!matchesAuthor(item, query.author())) {
                continue;
            }
            if (!matchesFlag(item, "top", query.top())) {
                continue;
            }
            if (!matchesFlag(item, "recommend", query.recommend())) {
                continue;
            }
            if (query.keyword() != null && !query.keyword().isBlank()) {
                String title = item.title();
                if (title == null || !title.toLowerCase().contains(query.keyword().toLowerCase())) {
                    continue;
                }
            }
            if (!matchesWhere(item, query.where())) {
                continue;
            }
            if (query.childrenOfAnchor() && item.parentId() != query.anchorId()) {
                continue;
            }
            if (query.siblingsOfAnchor() && item.parentId() != query.anchorParentId()) {
                continue;
            }
            if (!matchesRelate(item, query.relate(), anchor)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private boolean matchesStatus(ContentItem item, String status) {
        if (status == null || "any".equals(status)) {
            return notExpired(item);
        }
        if (!"PUBLISHED".equals(status)) {
            return status.equals(text(item.get("status"))) && notExpired(item);
        }
        return eligible(item);
    }

    /** 到期窗口与状态无关：到期内容自动退出（§6.3 v2.2）；判定函数另外还要求 {@code PUBLISHED}。 */
    private boolean notExpired(ContentItem item) {
        LocalDateTime expireTime = timeOf(item.get("expireTime"));
        return expireTime == null || expireTime.isAfter(now);
    }

    private boolean matchesType(ContentItem item, String typeCode) {
        return typeCode == null || "all".equals(typeCode) || Objects.equals(typeCode, item.typeCode());
    }

    private boolean matchesCategories(ContentItem item, Set<Long> categoryIds) {
        if (categoryIds == null) {
            return true;   // null = 不限栏目（category='all'）
        }
        if (categoryIds.isEmpty()) {
            return false;  // 解析不出来的分类：一条都不匹配，而不是"不限"
        }
        for (Object value : valuesOf(item.get("categoryId"))) {
            Long id = resolveCategory(compareText(value));
            if (id != null && categoryIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAuthor(ContentItem item, String author) {
        if (author == null || "all".equals(author)) {
            return true;
        }
        // author='self' 的坐标由标签放进 ContentQuery.anchor()，这里只按值比
        return Objects.equals(author, text(item.get("authorId")))
                || Objects.equals(author, text(item.get("authorSlug")));
    }

    private boolean matchesWhere(ContentItem item, List<WhereCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (WhereCondition condition : conditions) {
            if (!matches(item, condition)) {
                return false;
            }
        }
        return true;
    }

    /** 一个 {@code where} 条件（§2.5 的 9 个运算符全部在这里落地）。 */
    private boolean matches(ContentItem item, WhereCondition condition) {
        String code = condition.fieldCode();
        List<String> expected = condition.values();
        String first = expected.isEmpty() ? "" : expected.get(0);
        switch (condition.op()) {
            case has -> {
                return containsAny(item.get(code), expected);
            }
            case in -> {
                return containsAny(item.get(code), expected);
            }
            case eq -> {
                return matchesAnyValue(item.get(code), value -> value.equals(first));
            }
            case ne -> {
                // 字段缺失时 ne 不成立（§1.3："报错优于静默"，这里取"说不清就不匹配"）
                return item.has(code) && matchesAnyValue(item.get(code), value -> !value.equals(first));
            }
            case like -> {
                String needle = first.toLowerCase();
                return matchesAnyValue(item.get(code), value -> value.toLowerCase().contains(needle));
            }
            case gt -> {
                return compareAll(item.get(code), first, c -> c > 0);
            }
            case gte -> {
                return compareAll(item.get(code), first, c -> c >= 0);
            }
            case lt -> {
                return compareAll(item.get(code), first, c -> c < 0);
            }
            case lte -> {
                return compareAll(item.get(code), first, c -> c <= 0);
            }
        }
        return false;
    }

    private boolean matchesRelate(ContentItem item, String relate, ContentItem anchor) {
        if (relate == null || anchor == null) {
            return true;
        }
        List<String> tokens;
        if ("tag".equals(relate)) {
            tokens = tokensOf(anchor.get("tagId"), true);
        } else if ("category".equals(relate)) {
            tokens = tokensOf(anchor.get("categoryId"), false);
        } else if (relate.startsWith("field:")) {
            // relate='field:<code>'：锚定项的 RELATION 数组里出现的候选内容（数组顺序 = 人工顺序）
            return containsAny(item.get("id"), valuesOf(anchor.get(relate.substring("field:".length()))));
        } else {
            return true;
        }
        Object actual = "tag".equals(relate) ? item.get("tagId") : item.get("categoryId");
        for (String token : tokens) {
            if (containsAny(actual, List.of(token))) {
                return true;
            }
        }
        return false;
    }

    /** 一组 id 的全部可匹配写法（数字 id + slug），供 {@code relate} 用。 */
    private List<String> tokensOf(Object values, boolean tag) {
        List<String> tokens = new ArrayList<>();
        for (Object value : valuesOf(values)) {
            if (tag) {
                tokens.addAll(tagTokens(text(value)));
            } else {
                tokens.addAll(categoryTokens(text(value)));
            }
        }
        return tokens;
    }

    /** 排序：模板/类型给的字段顺序，**末尾恒追加 {@code id desc}**（§6.3 v2.2 定死）。 */
    private List<ContentItem> sorted(List<ContentItem> items, ContentQuery query) {
        String relationField = query.relate() != null && query.relate().startsWith("field:")
                ? query.relate().substring("field:".length()) : null;
        if (query.relationOrder() && query.anchorId() > 0 && relationField != null) {
            ContentItem anchor = contents.get(query.anchorId());
            if (anchor != null) {
                Map<String, Integer> order = new LinkedHashMap<>();
                int index = 0;
                for (Object id : valuesOf(anchor.get(relationField))) {
                    order.put(compareText(id), index++);
                }
                List<ContentItem> result = new ArrayList<>(items);
                result.sort(Comparator.comparingInt(
                        item -> order.getOrDefault(String.valueOf(item.id()), Integer.MAX_VALUE)));
                return result;
            }
        }
        List<OrderBy> orderby = query.orderby().isEmpty()
                ? defaultOrder(type(query.typeCode())) : query.orderby();
        List<ContentItem> result = new ArrayList<>(items);
        result.sort(comparator(orderby));
        return result;
    }

    /** 末尾恒追加 {@code id desc}：没有 tie-break 时同序内容会在分页里重复出现或整条丢失（§6.3）。 */
    private static Comparator<ContentItem> comparator(List<OrderBy> orderby) {
        Comparator<ContentItem> comparator = null;
        for (OrderBy order : orderby) {
            Comparator<ContentItem> next = (left, right) ->
                    compareValues(left.get(order.fieldCode()), right.get(order.fieldCode()));
            if (order.desc()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        Comparator<ContentItem> tieBreak = Comparator.comparingLong(ContentItem::id).reversed();
        return comparator == null ? tieBreak : comparator.thenComparing(tieBreak);
    }

    /** 类型的默认排序：{@code sort_field} / {@code sort_order}，未配时 {@code publishTime desc}（§6.3）。 */
    private List<OrderBy> defaultOrder(ContentTypeDef def) {
        String field = def == null ? null : def.sortFieldOrDefault();
        boolean desc = def == null || def.sortOrder() == null || !"asc".equalsIgnoreCase(def.sortOrder());
        return List.of(new OrderBy(field == null ? "publishTime" : field, desc));
    }

    /** 分类过滤集合；{@code all} / null 返回 null 表示不限。 */
    private Set<Long> categoryFilter(String category, boolean includeChildren) {
        if (category == null || "all".equals(category)) {
            return null;
        }
        Long id = resolveCategory(category);
        if (id == null) {
            return Set.of();
        }
        if (!includeChildren) {
            return Set.of(id);
        }
        Set<Long> ids = new LinkedHashSet<>();
        collectCategoryIds(id, ids);
        return ids;
    }

    private void collectCategoryIds(long id, Set<Long> out) {
        if (!out.add(id)) {
            return;
        }
        for (NavItem node : categories.values()) {
            if (parentIdOf(node) != null && parentIdOf(node) == id) {
                collectCategoryIds(node.id(), out);
            }
        }
    }

    private long categoryCount(long categoryId, CountScope scope) {
        Set<Long> ids = new LinkedHashSet<>();
        if (scope == CountScope.tree) {
            collectCategoryIds(categoryId, ids);
        } else {
            ids.add(categoryId);
        }
        long count = 0;
        for (ContentItem item : contents.values()) {
            if (eligible(item) && matchesCategories(item, ids)) {
                count++;
            }
        }
        return count;
    }

    private long typeCount(String typeCode) {
        long count = 0;
        for (ContentItem item : contents.values()) {
            if (eligible(item) && Objects.equals(typeCode, item.typeCode())) {
                count++;
            }
        }
        return count;
    }

    /* ---------------- 值的比较与转换 ---------------- */

    /** 字段值 vs 期望值：多值字段任一元素命中即算命中。 */
    private static boolean matchesAnyValue(Object actual, java.util.function.Predicate<String> test) {
        for (Object value : valuesOf(actual)) {
            if (test.test(compareText(value))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 比较用的文本形态。布尔归一到 {@code 1} / {@code 0}：模板写 {@code where='top:eq:1'}，
     * 而内存里的 {@code top} 可能是 {@code Boolean}（{@code ContentSpec.top(true)}），
     * 两者的字面量必须对得上——真实现里它是 {@code smallint}，也就是 1 / 0（§2.3）。
     */
    private static String compareText(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        return text(value);
    }

    private static boolean compareAll(Object actual, String expected, java.util.function.IntPredicate test) {
        boolean any = false;
        for (Object value : valuesOf(actual)) {
            Integer compared = compare(value, expected);
            if (compared == null) {
                return false;
            }
            any = true;
            if (!test.test(compared)) {
                return false;
            }
        }
        return any;
    }

    /** 数值按数值比、时间按时间比、其余按字符串比；不可比返回 null。 */
    private static Integer compare(Object actual, String expected) {
        if (actual == null) {
            return null;
        }
        LocalDateTime leftTime = actual instanceof LocalDateTime time ? time : parseTime(text(actual));
        LocalDateTime rightTime = parseTime(expected);
        if (leftTime != null && rightTime != null) {
            return leftTime.compareTo(rightTime);
        }
        Double leftNumber = parseNumber(actual);
        Double rightNumber = parseNumber(expected);
        if (leftNumber != null && rightNumber != null) {
            return Double.compare(leftNumber, rightNumber);
        }
        return text(actual).compareTo(expected);
    }

    /** 排序用的比较：任何一方为空都排到最后，其余按 {@link #compare}。 */
    private static int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        Integer compared = compare(left, text(right));
        return compared == null ? 0 : compared;
    }

    /** 多值字段的元素（{@code in} / {@code has} 用）；标量包一层。 */
    private static List<Object> valuesOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof java.util.Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(value, i));
            }
            return list;
        }
        return List.of(value);
    }

    private static boolean containsAny(Object actual, List<?> expected) {
        List<Object> candidates = valuesOf(actual);
        for (Object candidate : candidates) {
            String text = compareText(candidate);
            for (Object want : expected) {
                if (text.equals(compareText(want))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Boolean truthyNumber(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = text(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    /** {@code top} / {@code recommend} 的值可能是 Boolean 也可能是 0/1，统一成布尔再比。 */
    private static boolean matchesFlag(ContentItem item, String code, Boolean expected) {
        return expected == null || expected.equals(truthyNumber(item.get(code)));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Double parseNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(text(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 时间的归一：支持 {@code LocalDateTime} / {@code OffsetDateTime} / ISO 文本（{@code T} 或空格分隔）。 */
    public static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // 继续试下面的格式
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续试下面的格式
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalDateTime timeOf(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof OffsetDateTime time) {
            return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        return parseTime(text(value));
    }

    /* ---------------- 分类 / 标签的解析 ---------------- */

    private Long resolveCategory(String idOrSlug) {
        if (idOrSlug == null || "all".equals(idOrSlug)) {
            return null;
        }
        Long bySlug = categoryIdsBySlug.get(idOrSlug);
        if (bySlug != null) {
            return bySlug;
        }
        try {
            long id = Long.parseLong(idOrSlug.trim());
            return categories.containsKey(id) ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveTag(String idOrSlug) {
        if (idOrSlug == null) {
            return null;
        }
        Long bySlug = tagIdsBySlug.get(idOrSlug);
        if (bySlug != null) {
            return bySlug;
        }
        try {
            long id = Long.parseLong(idOrSlug.trim());
            return tags.containsKey(id) ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 一个标签条件的可匹配写法：id（{@code cms_content_index} 里存的就是它）与 slug 都算命中。
     *
     * <p>为什么两种都认：§2.5 v2.2 定死"值一律用 id"，但 {@code tag='hot'} 这种 slug 写法在模板里
     * 更自然，而且真实现拿到 slug 也会先解析成 id 再查索引。内存实现跟着认两种，
     * 否则"用 slug 写的模板"在测试里会静默查不到东西。
     */
    private List<String> tagTokens(String idOrSlug) {
        Long id = resolveTag(idOrSlug);
        if (id == null) {
            return List.of(idOrSlug);
        }
        NavItem node = tags.get(id);
        return List.of(String.valueOf(id), String.valueOf(node.get("slug")));
    }

    /** {@code category} 参数（id 或 slug）的可匹配写法。 */
    private List<String> categoryTokens(String idOrSlug) {
        Long id = resolveCategory(idOrSlug);
        if (id == null) {
            return List.of(idOrSlug);
        }
        NavItem node = categories.get(id);
        return List.of(String.valueOf(id), String.valueOf(node.get("slug")));
    }

    private static Long parentIdOf(NavItem node) {
        Object value = node.get("parentId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence sequence) {
            try {
                return Long.parseLong(sequence.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void sortNav(List<NavItem> items, String orderby) {
        if (orderby == null || orderby.isBlank() || "sort".equals(orderby)) {
            return;
        }
        if ("count".equals(orderby)) {
            items.sort(Comparator.comparingLong((NavItem item) -> longOf(item.get("count"))).reversed());
        } else if ("name".equals(orderby)) {
            items.sort(Comparator.comparing(item -> text(item.get("name"))));
        }
    }

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /* ---------------- 内容项的组装 ---------------- */

    /** 把一条 {@link ContentSpec} 变成统一的迭代项：内置列 + 自定义字段 + 派生字段（§6.3）。 */
    private ContentItem materialize(ContentSpec spec) {
        ContentTypeDef def = types.get(spec.typeCode);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", spec.id);
        values.put("typeCode", spec.typeCode);
        values.put("typeName", def == null ? spec.typeCode : def.name());
        values.put("title", spec.title);
        values.put("slug", spec.slug);
        values.put("summary", spec.summary);
        values.put("cover", spec.cover);
        values.put("status", spec.status);
        values.put("publishTime", spec.publishTime);
        values.put("expireTime", spec.expireTime);
        values.put("createTime", spec.createTime);
        values.put("updateTime", spec.updateTime);
        values.put("sort", spec.sort);
        values.put("top", spec.top ? 1 : 0);
        values.put("recommend", spec.recommend ? 1 : 0);
        values.put("authorId", spec.authorId);
        values.put("authorName", spec.authorName);
        values.put("authorSlug", spec.authorSlug);
        values.put("viewCount", spec.viewCount);
        values.put("viewCountDay", spec.viewCountDay);
        values.put("viewCountWeek", spec.viewCountWeek);
        values.put("commentCount", spec.commentCount);
        values.put("seoTitle", spec.title);
        values.put("contentFormat", spec.contentHtml == null ? "RICHTEXT" : "MARKDOWN");
        values.put("parentId", spec.parentId);
        values.put("content", spec.contentHtml);
        values.put("contentHtml", spec.contentHtml);

        String url = itemUrl.apply(ContentItem.of(Map.of("id", spec.id, "typeCode", spec.typeCode,
                "slug", spec.slug == null ? "" : spec.slug)));
        values.put("url", url);
        values.put("canonical", site.url() + url);

        List<Object> categoryIds = new ArrayList<>();
        List<Map<String, Object>> categoryList = new ArrayList<>();
        for (String idOrSlug : spec.categories) {
            Long id = resolveCategory(idOrSlug);
            if (id == null) {
                continue;
            }
            NavItem node = categories.get(id);
            categoryIds.add(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("name", node.get("name"));
            entry.put("slug", node.get("slug"));
            entry.put("url", node.get("url"));
            entry.put("dimension", node.get("dimension"));
            categoryList.add(entry);
        }
        values.put("categoryId", categoryIds);
        values.put("categories", categoryList);
        if (!categoryList.isEmpty()) {
            Map<String, Object> primary = categoryList.get(0);
            values.put("categoryName", primary.get("name"));
            values.put("categoryUrl", primary.get("url"));
            values.put("categoryPath", pathOf(longOf(primary.get("id"))));
        }

        List<Object> tagIds = new ArrayList<>();
        List<Map<String, Object>> tagList = new ArrayList<>();
        for (String idOrSlug : spec.tags) {
            Long id = resolveTag(idOrSlug);
            if (id == null) {
                continue;
            }
            NavItem node = tags.get(id);
            tagIds.add(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("name", node.get("name"));
            entry.put("slug", node.get("slug"));
            entry.put("url", node.get("url"));
            tagList.add(entry);
        }
        values.put("tagId", tagIds);
        values.put("tags", tagList);

        if (spec.parentId > 0) {
            // parentTitle / ancestors 等"要看得到别人"的派生字段在 finishDerivedFields() 里补
            values.put("parentId", spec.parentId);
        }

        String body = spec.contentHtml == null ? "" : spec.contentHtml;
        values.put("wordCount", (long) body.replaceAll("<[^>]*>", "").length());
        values.put("readingMinutes", Math.max(1L, Math.round(body.replaceAll("<[^>]*>", "").length() / 500.0)));
        values.put("imageCount", (long) countOccurrences(body, "<img"));
        values.put("updatedDaysAgo", 0L);
        values.put("ratingAvg", 0.0);
        values.put("ratingCount", 0L);

        values.putAll(spec.fields);
        return ContentItem.of(values);
    }

    /** 分类的 {@code path}：从顶级到自身，{@code /} 分隔（面包屑用）。 */
    private String pathOf(long categoryId) {
        StringBuilder sb = new StringBuilder();
        for (NavItem node : categoryAncestors(categoryId)) {
            sb.append('/').append(node.get("slug"));
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /* ================= 造数据 ================= */

    /** 一条待放入内存库的内容（{@link Builder#content(ContentSpec)} 的参数）。 */
    public static final class ContentSpec {

        private long id;
        private String typeCode;
        private String slug;
        private String title;
        private String summary;
        private String cover;
        private String status = "PUBLISHED";
        private String publishTime;
        private String expireTime;
        private String createTime;
        private String updateTime;
        private int sort;
        private boolean top;
        private boolean recommend;
        private String authorId;
        private String authorName;
        private String authorSlug;
        private long parentId;
        private long viewCount;
        private long viewCountDay;
        private long viewCountWeek;
        private long commentCount;
        private String contentHtml;
        private final List<String> categories = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();
        private final Map<String, Object> fields = new LinkedHashMap<>();

        public static ContentSpec of(long id, String typeCode, String slug, String title) {
            ContentSpec spec = new ContentSpec();
            spec.id = id;
            spec.typeCode = typeCode;
            spec.slug = slug;
            spec.title = title;
            return spec;
        }

        public ContentSpec summary(String value) {
            this.summary = value;
            return this;
        }

        public ContentSpec cover(String value) {
            this.cover = value;
            return this;
        }

        /** {@code PUBLISHED}（默认）/ {@code DRAFT} / {@code OFFLINE}…；判定函数只放行 {@code PUBLISHED}。 */
        public ContentSpec status(String value) {
            this.status = value;
            return this;
        }

        /** 发布时间，ISO 文本（{@code 2026-03-01T10:00}）；晚于站点 {@code now} = 还没到点。 */
        public ContentSpec publishTime(String value) {
            this.publishTime = value;
            return this;
        }

        public ContentSpec expireTime(String value) {
            this.expireTime = value;
            return this;
        }

        public ContentSpec createTime(String value) {
            this.createTime = value;
            return this;
        }

        public ContentSpec updateTime(String value) {
            this.updateTime = value;
            return this;
        }

        public ContentSpec sort(int value) {
            this.sort = value;
            return this;
        }

        public ContentSpec top(boolean value) {
            this.top = value;
            return this;
        }

        public ContentSpec recommend(boolean value) {
            this.recommend = value;
            return this;
        }

        public ContentSpec author(String id, String name, String authorSlug) {
            this.authorId = id;
            this.authorName = name;
            this.authorSlug = authorSlug;
            return this;
        }

        public ContentSpec parent(long value) {
            this.parentId = value;
            return this;
        }

        public ContentSpec viewCount(long value) {
            this.viewCount = value;
            return this;
        }

        public ContentSpec viewCountDay(long value) {
            this.viewCountDay = value;
            return this;
        }

        public ContentSpec viewCountWeek(long value) {
            this.viewCountWeek = value;
            return this;
        }

        public ContentSpec commentCount(long value) {
            this.commentCount = value;
            return this;
        }

        /** 正文切片对象：正文分页切的就是它（§5.2.3）。 */
        public ContentSpec content(String value) {
            this.contentHtml = value;
            return this;
        }

        /** 追加一个分类（id 或 slug）。 */
        public ContentSpec category(String idOrSlug) {
            this.categories.add(idOrSlug);
            return this;
        }

        /** 追加一个标签（id 或 slug）。 */
        public ContentSpec tag(String idOrSlug) {
            this.tags.add(idOrSlug);
            return this;
        }

        /** 自定义字段值。排序 / 过滤都读它。 */
        public ContentSpec field(String code, Object value) {
            this.fields.put(code, value);
            return this;
        }
    }

    /** 内存库的组装者；{@link ContentScope} 继承它，好让"加一条内容"之后的链式调用两种意图都成立。 */
    public static class Builder {

        private SiteConfig site;
        private Function<ContentItem, String> itemUrl = item -> "/" + item.typeCode() + "/" + item.slug() + ".html";
        private LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);

        private final Map<String, ContentTypeDef> types = new LinkedHashMap<>();
        private final Map<String, FormDef> forms = new LinkedHashMap<>();
        private final List<String> menuCodes = new ArrayList<>();
        private final Map<String, List<NavItem>> menus = new LinkedHashMap<>();
        private final Map<Long, NavItem> categories = new LinkedHashMap<>();
        private final Map<String, Long> categoryIdsBySlug = new LinkedHashMap<>();
        private final Map<Long, NavItem> tags = new LinkedHashMap<>();
        private final Map<String, Long> tagIdsBySlug = new LinkedHashMap<>();
        private final List<ContentSpec> contents = new ArrayList<>();

        Builder() {
            this.site = new SiteConfig(1L, "test", "测试站", "example.com", "https", "/logo.png", "zh-CN",                    "测试站的描述", "测试,站点", "测试站的描述", "京ICP备00000000号", "010-00000000",
                    "hi@example.com", "sites/test/www", "/static/default-cover.png", "/static/og.png",
                    "default", "", "", Map.of());
        }

        /**
         * 判定函数的 {@code now}（§6.3 的时间窗）。
         * **默认 {@code 2026-06-01T12:00}**；{@code publishTime} 晚于它的内容一律不可见。
         */
        public Builder now(LocalDateTime value) {
            this.now = value;
            return this;
        }

        /** 覆盖站点的某个发布选项（{@code SiteConfig.options}）。 */
        public Builder siteOption(String code, Object value) {
            Map<String, Object> options = new LinkedHashMap<>(site.options());
            options.put(code, value);
            return site(new SiteConfig(site.id(), site.code(), site.name(), site.domain(),
                    site.protocol(), site.logo(), site.lang(), site.description(), site.keywords(),
                    site.seoDescription(), site.icp(), site.contactPhone(), site.contactEmail(),
                    site.rootDir(), site.defaultCover(), site.ogImage(), site.theme(),
                    site.statisticsCode(), site.mediaHost(), options));
        }

        /** 整份替换站点配置（例如换一个 {@code defaultCover} 来验证 §5.5 第（1）条）。 */
        public Builder site(SiteConfig value) {
            this.site = value;
            return this;
        }

        /** 一条内容的 URL 怎么算（{@code UrlResolver} 在期 2 落地，这里默认 {@code /{type}/{slug}.html}）。 */
        public Builder itemUrl(Function<ContentItem, String> value) {
            this.itemUrl = value;
            return this;
        }

        /** 注册一个内容类型定义。 */
        public Builder type(ContentTypeDef def) {
            types.put(def.code(), def);
            return this;
        }

        /**
         * 注册一个内容类型定义；{@code perPage} / {@code sortField} / {@code paginateBody} 走默认
         * （20 / {@code publishTime desc} / 不分页正文）。
         */
        public Builder type(long id, String code, String name, ContentTypeDef.Kind kind) {
            return type(ContentTypeDef.of(id, code, name, kind));
        }

        /** 覆盖已注册类型的 {@code per_page}（{@code {cms:list}} 的 {@code row} 缺省值来自它）。 */
        public Builder perPage(String typeCode, int perPage) {
            ContentTypeDef def = requireType(typeCode);
            types.put(typeCode, new ContentTypeDef(def.id(), def.code(), def.name(), def.kind(),
                    def.hierarchical(), def.detailUrlPattern(), def.listUrlPattern(), def.detailTemplate(),
                    def.listTemplate(), def.paginateBody(), def.sortField(), def.sortOrder(), perPage,
                    def.seoTitleField(), def.seoDescField(), def.fields(), def.options()));
            return this;
        }

        /** 覆盖已注册类型的默认排序（{@code {cms:list}} 的 {@code orderby} 缺省值来自它）。 */
        public Builder sort(String typeCode, String field, String order) {
            ContentTypeDef def = requireType(typeCode);
            types.put(typeCode, new ContentTypeDef(def.id(), def.code(), def.name(), def.kind(),
                    def.hierarchical(), def.detailUrlPattern(), def.listUrlPattern(), def.detailTemplate(),
                    def.listTemplate(), def.paginateBody(), field, order, def.perPage(),
                    def.seoTitleField(), def.seoDescField(), def.fields(), def.options()));
            return this;
        }

        /** 覆盖已注册类型的 {@code paginate_body}（非空 = 该类型走正文分页，§5.6）。 */
        public Builder paginateBody(String typeCode, String field) {
            ContentTypeDef def = requireType(typeCode);
            types.put(typeCode, new ContentTypeDef(def.id(), def.code(), def.name(), def.kind(),
                    def.hierarchical(), def.detailUrlPattern(), def.listUrlPattern(), def.detailTemplate(),
                    def.listTemplate(), field, def.sortField(), def.sortOrder(), def.perPage(),
                    def.seoTitleField(), def.seoDescField(), def.fields(), def.options()));
            return this;
        }

        /** 给已注册类型加一个字段定义。 */
        public Builder field(String typeCode, FieldDef def) {
            ContentTypeDef type = requireType(typeCode);
            List<FieldDef> fields = new ArrayList<>(type.fields());
            fields.add(def);
            types.put(typeCode, new ContentTypeDef(type.id(), type.code(), type.name(), type.kind(),
                    type.hierarchical(), type.detailUrlPattern(), type.listUrlPattern(),
                    type.detailTemplate(), type.listTemplate(), type.paginateBody(), type.sortField(),
                    type.sortOrder(), type.perPage(), type.seoTitleField(), type.seoDescField(),
                    fields, type.options()));
            return this;
        }

        /** 建一个可筛选的自定义字段（{@code indexed=1}，§2.5）。 */
        public Builder field(String typeCode, String code, FieldType fieldType) {
            return field(typeCode, FieldDef.indexed(typeCode, code, fieldType));
        }

        /** 建一个可筛选的枚举字段，选项按 {@code 值:标签} 传入。 */
        public Builder enumField(String typeCode, String code, String... valueLabelPairs) {
            List<EnumOption> options = new ArrayList<>();
            for (String pair : valueLabelPairs) {
                int at = pair.indexOf(':');
                options.add(at < 0 ? new EnumOption(pair, pair)
                        : new EnumOption(pair.substring(0, at), pair.substring(at + 1)));
            }
            return field(typeCode, FieldDef.builder(typeCode, code, FieldType.ENUM_MULTI)
                    .indexed(true).options(options).build());
        }

        public Builder form(FormDef def) {
            forms.put(def.code(), def);
            return this;
        }

        public Builder menu(String code, List<NavItem> items) {
            if (!menuCodes.contains(code)) {
                menuCodes.add(code);
            }
            menus.put(code, List.copyOf(items));
            return this;
        }

        /** 一个分类节点。 */
        public Builder category(long id, long parentId, String slug, String name) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            values.put("parentId", parentId);
            values.put("slug", slug);
            values.put("name", name);
            values.put("label", name);
            values.put("url", "/" + slug + "/");
            categories.put(id, NavItem.of(values));
            categoryIdsBySlug.put(slug, id);
            return this;
        }

        /**
         * 只给 slug 的便捷写法：没注册过就新建一个顶级分类。
         *
         * <p>{@code .content(...).category("news")} 与 {@code ContentSpec.category("news")} 同名——
         * 前者是"建分类"，后者是"把内容挂到这个分类上"。两种写法在链式调用里读起来一致。
         */
        public Builder category(String slug) {
            return category(slug, slug);
        }

        /** 只给 slug 的便捷写法：名称与 slug 相同。 */
        public Builder category(String slug, String name) {
            if (categoryIdsBySlug.containsKey(slug)) {
                return this;
            }
            return category(nextCategoryId(), 0L, slug, name);
        }

        private long nextCategoryId() {
            long id = 1;
            while (categories.containsKey(id)) {
                id++;
            }
            return id;
        }

        /** 一个标签节点。 */
        public Builder tag(long id, String slug, String name) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            values.put("slug", slug);
            values.put("name", name);
            values.put("label", name);
            values.put("url", "/tag/" + slug + "/");
            tags.put(id, NavItem.of(values));
            tagIdsBySlug.put(slug, id);
            return this;
        }

        /** 只给 slug 的便捷写法：没注册过就新建一个标签（名称与 slug 相同）。 */
        public Builder tag(String slug) {
            if (tagIdsBySlug.containsKey(slug)) {
                return this;
            }
            long id = 1;
            while (tags.containsKey(id)) {
                id++;
            }
            return tag(id, slug, slug);
        }

        public Builder content(ContentSpec spec) {
            contents.add(spec);
            return this;
        }

        /**
         * 便捷写法：加一条内容并返回一个**只看这条内容**的链式入口（继承 {@code Builder}，
         * 因此后面接 {@code .category(...)} / {@code .build()} 都不变）。
         *
         * <p>两种写法都成立，而且不会因为"链到谁身上"而行为不同：
         * <pre>
         * .content(11, "article", "a", "文章A").publishTime("2026-05-01T10:00").category("news")
         * .content(11, "article", "a", "文章A").category("news")
         * .content(ContentSpec.of(11, "article", "a", "文章A").publishTime("2026-05-01T10:00"))
         * </pre>
         * 第二条里的 {@code category} 落在"这条内容"上（挂分类），第三条写在 {@code ContentSpec} 上
         * 是同一件事；两条都会在分类不存在时**自动建一个同名 slug 的顶级分类**。
         */
        public ContentScope content(long id, String typeCode, String slug, String title) {
            ContentSpec spec = ContentSpec.of(id, typeCode, slug, title);
            return new ContentScope(this, spec);
        }

        public TestContentProvider build() {
            return new TestContentProvider(this);
        }

        private ContentTypeDef requireType(String typeCode) {
            ContentTypeDef def = types.get(typeCode);
            if (def == null) {
                throw new IllegalArgumentException("先 type(...) 再配 " + typeCode);
            }
            return def;
        }
    }

    /**
     * {@code .content(id, type, slug, title)} 之后的链式入口：**先看这条内容**，看不到的再看整库。
     *
     * <p>为什么要有它：{@code .content(11, "article", "a", "A").publishTime(…)} 与
     * {@code .content(11, …).category("news")} 都必须成立，而 {@code publishTime} 是
     * {@link ContentSpec} 的方法、{@code category(String)} 两边都有同名的（一个是"挂分类"、
     * 一个是"建分类"）。这里覆盖两边的全套方法，把每条转发到对的地方：
     * 内容自己的字段 → {@link ContentSpec}；库级操作 → 原来的 {@link Builder}。
     *
     * <p>**不继承 {@code Builder}**：继承会造出第二个空库，加了内容却不在库里。
     */
    public static final class ContentScope {

        private final Builder outer;
        private final ContentSpec current;

        private ContentScope(Builder outer, ContentSpec spec) {
            this.outer = outer;
            this.current = spec;
            outer.content(spec);
        }

        /* ---------------- 这条内容自己的字段 ---------------- */

        public ContentScope summary(String value) {
            current.summary(value);
            return this;
        }

        public ContentScope cover(String value) {
            current.cover(value);
            return this;
        }

        public ContentScope status(String value) {
            current.status(value);
            return this;
        }

        public ContentScope publishTime(String value) {
            current.publishTime(value);
            return this;
        }

        public ContentScope expireTime(String value) {
            current.expireTime(value);
            return this;
        }

        public ContentScope createTime(String value) {
            current.createTime(value);
            return this;
        }

        public ContentScope updateTime(String value) {
            current.updateTime(value);
            return this;
        }

        public ContentScope sort(int value) {
            current.sort(value);
            return this;
        }

        public ContentScope top(boolean value) {
            current.top(value);
            return this;
        }

        public ContentScope recommend(boolean value) {
            current.recommend(value);
            return this;
        }

        public ContentScope author(String id, String name, String slug) {
            current.author(id, name, slug);
            return this;
        }

        public ContentScope parent(long value) {
            current.parent(value);
            return this;
        }

        public ContentScope viewCount(long value) {
            current.viewCount(value);
            return this;
        }

        public ContentScope viewCountDay(long value) {
            current.viewCountDay(value);
            return this;
        }

        public ContentScope viewCountWeek(long value) {
            current.viewCountWeek(value);
            return this;
        }

        public ContentScope commentCount(long value) {
            current.commentCount(value);
            return this;
        }

        public ContentScope content(String value) {
            current.content(value);
            return this;
        }

        /** 给这条内容挂一个分类；分类还没有时自动建一个（名称 = slug）。 */
        public ContentScope category(String idOrSlug) {
            outer.category(idOrSlug);
            current.category(idOrSlug);
            return this;
        }

        /** 给这条内容挂一个标签；标签还没有时自动建一个。 */
        public ContentScope tag(String idOrSlug) {
            outer.tag(idOrSlug);
            current.tag(idOrSlug);
            return this;
        }

        public ContentScope field(String code, Object value) {
            current.field(code, value);
            return this;
        }

        /* ---------------- 库级操作（转回原来的 Builder） ---------------- */

        public Builder now(java.time.LocalDateTime value) {
            return outer.now(value);
        }

        public Builder siteOption(String code, Object value) {
            return outer.siteOption(code, value);
        }

        public Builder site(SiteConfig value) {
            return outer.site(value);
        }

        public Builder itemUrl(java.util.function.Function<ContentItem, String> value) {
            return outer.itemUrl(value);
        }

        public Builder type(ContentTypeDef def) {
            return outer.type(def);
        }

        public Builder type(long id, String code, String name, ContentTypeDef.Kind kind) {
            return outer.type(id, code, name, kind);
        }

        public Builder perPage(String typeCode, int perPage) {
            return outer.perPage(typeCode, perPage);
        }

        public Builder sort(String typeCode, String field, String order) {
            return outer.sort(typeCode, field, order);
        }

        public Builder paginateBody(String typeCode, String field) {
            return outer.paginateBody(typeCode, field);
        }

        public Builder field(String typeCode, FieldDef def) {
            return outer.field(typeCode, def);
        }

        public Builder field(String typeCode, String code, FieldType fieldType) {
            return outer.field(typeCode, code, fieldType);
        }

        public Builder enumField(String typeCode, String code, String... valueLabelPairs) {
            return outer.enumField(typeCode, code, valueLabelPairs);
        }

        public Builder form(FormDef def) {
            return outer.form(def);
        }

        public Builder menu(String code, List<NavItem> items) {
            return outer.menu(code, items);
        }

        public Builder category(long id, long parentId, String slug, String name) {
            return outer.category(id, parentId, slug, name);
        }

        public Builder tag(long id, String slug, String name) {
            return outer.tag(id, slug, name);
        }

        public Builder content(ContentSpec spec) {
            return outer.content(spec);
        }

        public ContentScope content(long id, String typeCode, String slug, String title) {
            return outer.content(id, typeCode, slug, title);
        }

        public TestContentProvider build() {
            return outer.build();
        }
    }
}
