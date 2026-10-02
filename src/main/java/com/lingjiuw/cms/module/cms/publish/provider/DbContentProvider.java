package com.lingjiuw.cms.module.cms.publish.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.entity.CmsMenu;
import com.lingjiuw.cms.module.cms.entity.CmsMenuItem;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
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
import com.lingjiuw.cms.module.cms.publish.model.UrlPatternResolver;
import com.lingjiuw.cms.module.cms.publish.model.WhereCondition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库版 {@link ContentProvider}：把 static-publish.md §2 的内容模型读成标签实现要的形态。
 *
 * <p><b>它是"一个站点的一次发布"的取数出口</b>：构造时给一个 {@code siteId}，之后所有语句都带上它
 * （站点隔离在数据层，不在标签层）。Spring 的 bean 是单例，而发布需要"某一个站点"的 provider，
 * 因此本类**不是** {@code @Component}，由 {@link DbContentProviderFactory} 按站点造实例。
 *
 * <p>四条口径只在数据层实现一次（标签与模板都不许再算一遍）：
 * <ul>
 *   <li><b>时间窗</b>（§6.3）：{@code PUBLISHED} 隐含 {@code publish_time <= now()} 且未到期；列表、
 *       计数、邻接、归档、facet、tags 全部走同一份 SQL 谓词（{@code CmsContentMapper.xml} 的
 *       {@code publishWindow}）；</li>
 *   <li><b>URL</b>（§7.1）：占位符替换全部走共享的 {@link UrlPatternResolver}，本类只负责把
 *       "这条内容 / 这个分类的取值"装进 values 表；</li>
 *   <li><b>派生字段</b>（§2.2）：{@code url} / {@code canonical} / {@code parent*} / {@code ancestors} /
 *       {@code childCount} / {@code category*} / {@code categories} / {@code tags} / {@code authorUrl} /
 *       {@code toc} / {@code wordCount} … 在这里算；{@code current} / {@code class} 由渲染层补（§5.5）；</li>
 *   <li><b>逻辑删除</b>：{@code BaseMapper} 的查询由 {@code @TableLogic} 拼 {@code deleted = 0}，
 *       手写 XML 的语句自己写（backend/AGENTS.md）。</li>
 * </ul>
 *
 * <p><b>性能</b>：一页内容的分类、标签、父内容、子内容数、被引用内容、作者、媒体都是**批量查询**后
 * 在内存里分发，不做逐条 SQL；分类树 / 标签表 / 计数器 / 单条内容按实例缓存（一次发布的快照语义）。
 */
public class DbContentProvider implements ContentProvider {

    /** 层级深度与祖先链的硬上限：坏数据里的环不该把发布卡死（§2.3 的保存期校验是第一道防线）。 */
    private static final int MAX_HIERARCHY = 32;

    /** §7.1：{@code detail_url_pattern} 为空时的兜底形态。 */
    private static final String FALLBACK_DETAIL_PATTERN = "/{typeCode}/{slug}.html";

    private final long siteId;
    private final ProviderMappers mappers;

    /* ---------------- 按实例的惰性缓存（寿命 = 一次发布） ---------------- */

    // 这些缓存**会被多个渲染 worker 线程并发读写**：SitePublishService 把同一个 provider 交给
    // 所有页的渲染任务（§8.3 的页级并发）。因此两条约束不能少：
    //   ① 容器本身线程安全（rowCache 用 ConcurrentHashMap，HashMap 并发 put 会写丢失甚至成环）；
    //   ② 惰性初始化的引用是 volatile，否则无同步的 check-then-act 没有 happens-before 保证，
    //      另一个线程可能读到"非 null 但尚未安全发布"的引用。
    private volatile SiteConfig site;
    private volatile Map<String, ContentTypeDef> typesByCode;
    private volatile Map<String, Integer> typeSort;
    private volatile List<CmsCategory> categoryRows;
    private volatile Map<Long, CmsCategory> categoryById;
    private volatile Map<String, CmsCategory> categoryBySlug;
    private volatile List<CmsTag> tagRows;
    private volatile Map<Long, CmsTag> tagById;
    private volatile Map<String, CmsTag> tagBySlug;
    private volatile Map<Long, Long> categoryNodeCounts;
    private volatile Map<Long, Long> tagContentCounts;
    private final Map<Long, ContentRow> rowCache = new ConcurrentHashMap<>();

    public DbContentProvider(long siteId, ProviderMappers mappers) {
        this.siteId = siteId;
        this.mappers = mappers;
    }

    @Override
    public long siteId() {
        return siteId;
    }

    /* ================= 站点配置（§2.7） ================= */

    @Override
    public SiteConfig site() {
        if (site == null) {
            site = loadSite();
        }
        return site;
    }

    private SiteConfig loadSite() {
        SiteRow row = mappers.siteMapper().selectPublishSite(siteId);
        Map<String, Object> options = loadOptions();
        Object mediaHost = options.get("media.host");
        if (row == null) {
            // 站点行不存在：给一个空壳而不是抛异常——发布任务会按"没有内容"正常失败
            return new SiteConfig(siteId, null, null, null, "https", null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    mediaHost == null ? "" : String.valueOf(mediaHost), options);
        }
        return new SiteConfig(
                row.getId() == null ? siteId : row.getId(), row.getCode(), row.getName(),
                row.getDomain(), blankTo(row.getProtocol(), "https"), row.getLogo(),
                blankTo(row.getLang(), "zh-CN"), row.getDescription(), row.getKeywords(),
                row.getSeoDescription(), row.getIcp(), row.getContactPhone(), row.getContactEmail(),
                row.getRootDir(), row.getDefaultCover(), row.getOgImage(), row.getTheme(),
                row.getStatisticsCode(),
                mediaHost == null ? "" : String.valueOf(mediaHost), options);
    }

    /**
     * 站点发布选项（§2.7）：{@code option_code → value}。空串 = "未设置"，**不进表**
     * （{@link SiteConfig#flag} / {@link SiteConfig#number} / {@code option(code, default)}
     * 都是"表里没有就用默认值"）。
     *
     * <p>值按 §2.7 那张表的类型解析：布尔选项给 {@code Boolean}、计数选项给 {@code Integer}、
     * 五个 JSON 形态的选项给解析后的 {@code List} / {@code Map}，其余保留原字符串。
     */
    private Map<String, Object> loadOptions() {
        List<CmsSitePublishOption> rows = mappers.optionMapper().selectList(
                new LambdaQueryWrapper<CmsSitePublishOption>()
                        .eq(CmsSitePublishOption::getSiteId, siteId)
                        .orderByAsc(CmsSitePublishOption::getOptionCode));
        Map<String, Object> options = new LinkedHashMap<>();
        for (CmsSitePublishOption row : rows) {
            Object value = parseOption(row.getOptionCode(), row.getValue());
            if (value != null) {
                options.put(row.getOptionCode(), value);
            }
        }
        return options;
    }

    /**
     * §2.7 里的开关选项（1 / 0）。
     *
     * <p>{@code public}：后台「发布选项」接口要用它判定 {@code valueType}，判定口径只能有一份。
     */
    public static final Set<String> BOOLEAN_OPTIONS = Set.of(
            "page.category", "page.tag", "page.taglist", "page.archive", "page.author", "page.facet",
            "page.search", "page.feed", "page.redirect", "seo.paginatedIndex", "seo.facetIndex",
            "feed.includeBody", "publish.preview", "publish.strict", "publish.expireRedirect",
            "comment.moderate", "comment.snapshot");

    /** §2.7 里存 JSON 字面量的五个选项（其余"逗号串"形态的选项保持字符串）。{@code public}：同 {@link #BOOLEAN_OPTIONS}。 */
    public static final Set<String> JSON_OPTIONS = Set.of(
            "pages.static", "facets.combos", "feed.types", "i18n.alternates", "seo.noindexTypes");

    /** §2.7 的计数选项。{@code public}：同 {@link #BOOLEAN_OPTIONS}。 */
    public static final Set<String> NUMBER_OPTIONS = Set.of(
            "neighbor.limit", "index.shardSize", "publish.keepReleases", "publish.threads",
            "publish.pageTimeout", "publish.debounce", "facets.maxPages", "facets.cardinality",
            "search.staticMax", "search.bodyChars", "sitemap.shardSize", "feed.size",
            "reading.speed", "page.tagMinCount", "comment.snapshotSize");

    /** 空串 = 未设置（{@code cms_site_publish_option.value} 的空串就是这个意思，§2.7）。 */
    static Object parseOption(String code, String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (BOOLEAN_OPTIONS.contains(code)) {
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            return raw;
        }
        if (JSON_OPTIONS.contains(code)) {
            Object parsed = Json.parse(text);
            return parsed == null ? raw : parsed;
        }
        if (NUMBER_OPTIONS.contains(code)) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException e) {
                return raw;
            }
        }
        return raw;
    }

    /** 站点选项的字符串取值；没有配置（或空串）时用默认值。 */
    private String option(String code, String defaultValue) {
        Object value = site().options().get(code);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private int numberOption(String code, int defaultValue) {
        Object value = site().options().get(code);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /* ================= 定义（§2.1 / §2.2） ================= */

    @Override
    public List<ContentTypeDef> types() {
        return List.copyOf(typeMap().values());
    }

    @Override
    public ContentTypeDef type(String typeCode) {
        return typeCode == null ? null : typeMap().get(typeCode);
    }

    /** 类型定义按 {@code sort, id} 排（§2.1），字段定义挂在各自的类型上（§2.2）。 */
    private Map<String, ContentTypeDef> typeMap() {
        if (typesByCode != null) {
            return typesByCode;
        }
        List<CmsContentType> rows = mappers.contentTypeMapper().selectList(
                new LambdaQueryWrapper<CmsContentType>()
                        .eq(CmsContentType::getSiteId, siteId)
                        .eq(CmsContentType::getStatus, 1)
                        .orderByAsc(CmsContentType::getSort)
                        .orderByAsc(CmsContentType::getId));
        Map<String, List<FieldDef>> fieldsByType = loadFields(rows);
        Map<String, ContentTypeDef> map = new LinkedHashMap<>();
        Map<String, Integer> sorts = new LinkedHashMap<>();
        for (CmsContentType row : rows) {
            map.put(row.getCode(), new ContentTypeDef(
                    row.getId() == null ? 0L : row.getId(), row.getCode(), row.getName(),
                    kindOf(row.getKind()), flag(row.getHierarchical()) || "TREE".equals(row.getKind()),
                    row.getDetailUrlPattern(), row.getListUrlPattern(), row.getDetailTemplate(),
                    row.getListTemplate(), row.getPaginateBody(), row.getSortField(), row.getSortOrder(),
                    row.getPerPage() == null ? 20 : row.getPerPage(), row.getSeoTitleField(),
                    row.getSeoDescField(), fieldsByType.getOrDefault(row.getCode(), List.of()),
                    Json.object(row.getOptions())));
            sorts.put(row.getCode(), row.getSort() == null ? 0 : row.getSort());
        }
        typeSort = sorts;
        typesByCode = map;
        return typesByCode;
    }

    private static ContentTypeDef.Kind kindOf(String text) {
        if (text != null) {
            for (ContentTypeDef.Kind kind : ContentTypeDef.Kind.values()) {
                if (kind.name().equalsIgnoreCase(text.trim())) {
                    return kind;
                }
            }
        }
        return ContentTypeDef.Kind.CONTENT;
    }

    /** {@code cms_field} 按类型分组，组内按 {@code sort, id}（§2.2）。 */
    private Map<String, List<FieldDef>> loadFields(List<CmsContentType> typeRows) {
        Map<String, List<FieldDef>> result = new LinkedHashMap<>();
        if (typeRows.isEmpty()) {
            return result;
        }
        List<String> codes = new ArrayList<>();
        for (CmsContentType row : typeRows) {
            codes.add(row.getCode());
        }
        List<CmsField> rows = mappers.fieldMapper().selectList(new LambdaQueryWrapper<CmsField>()
                .eq(CmsField::getSiteId, siteId)
                .in(CmsField::getTypeCode, codes)
                .orderByAsc(CmsField::getSort)
                .orderByAsc(CmsField::getId));
        for (CmsField row : rows) {
            result.computeIfAbsent(row.getTypeCode(), key -> new ArrayList<>()).add(toFieldDef(row));
        }
        return result;
    }

    /** 一行 {@code cms_field} → {@link FieldDef}；{@code options} 是 {@code 值:标签} 逗号分隔（§2.2）。 */
    private static FieldDef toFieldDef(CmsField row) {
        return FieldDef.builder(row.getTypeCode(), row.getCode(), fieldType(row.getFieldType()))
                .label(row.getLabel())
                .raw(flag(row.getRaw()))
                .indexed(flag(row.getIndexed()))
                .searchable(flag(row.getSearchable()))
                .required(flag(row.getRequired()))
                .defaultValue(row.getDefaultValue())
                .options(enumOptions(row.getOptions()))
                .formatter(row.getFormatter())
                .crossSite(flag(row.getCrossSite()))
                .sort(row.getSort() == null ? 0 : row.getSort())
                .build();
    }

    /** 19 种字段类型（§2.2）；库里出现表外取值时按 {@code TEXT} 处理（宁可输出文本，不打断发布）。 */
    static FieldType fieldType(String text) {
        if (text != null && !text.isBlank()) {
            for (FieldType type : FieldType.values()) {
                if (type.name().equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
        }
        return FieldType.TEXT;
    }

    /** {@code 值:标签} 逗号分隔 → 选项表；没有 {@code :} 时标签 = 值。 */
    static List<EnumOption> enumOptions(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<EnumOption> options = new ArrayList<>();
        for (String piece : text.split(",")) {
            String item = piece.trim();
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.indexOf(':');
            if (colon < 0) {
                options.add(new EnumOption(item, item));
            } else {
                String value = item.substring(0, colon).trim();
                String label = item.substring(colon + 1).trim();
                options.add(new EnumOption(value, label.isEmpty() ? value : label));
            }
        }
        return options;
    }

    @Override
    public FormDef form(String code) {
        // TODO: 期 3 的 cms_form / cms_form_field，本阶段这两张表还不存在（返回"没有"是正确行为）
        return null;
    }

    @Override
    public List<String> formCodes() {
        // TODO: 期 3 的 cms_form / cms_form_field，本阶段这两张表还不存在
        return List.of();
    }

    /* ================= 存在性检查（§4.5 第 7 条） ================= */

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
        if (idOrSlug == null || idOrSlug.isBlank()) {
            return false;
        }
        String text = idOrSlug.trim();
        Long id = asLong(text);
        if (id != null) {
            ContentRow row = findRow(id);
            return row != null && "author".equals(row.getTypeCode());
        }
        return findAuthorBySlug(text) != null;
    }

    /* ================= 单条内容 ================= */

    @Override
    public ContentItem content(String typeCode, long id) {
        ContentRow row = findRow(id);
        if (row == null || !row.getTypeCode().equals(typeCode)) {
            return null;
        }
        return item(row, 1, null);
    }

    @Override
    public ContentItem contentById(long id) {
        ContentRow row = findRow(id);
        return row == null ? null : item(row, 1, null);
    }

    @Override
    public ContentItem contentBySlug(String typeCode, String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<CmsContent> wrapper = new LambdaQueryWrapper<CmsContent>()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getSlug, slug)
                .orderByAsc(CmsContent::getId);
        if (typeCode != null) {
            wrapper.eq(CmsContent::getTypeCode, typeCode);
        }
        List<CmsContent> rows = mappers.contentMapper().selectList(wrapper);
        return rows.isEmpty() ? null : item(toRow(rows.get(0)), 1, null);
    }

    /** 单条内容（带实例缓存：一页内容的父内容 / 关联内容反复取同一条时只查一次）。 */
    private ContentRow findRow(long id) {
        ContentRow cached = rowCache.get(id);
        if (cached != null) {
            return cached;
        }
        List<CmsContent> rows = mappers.contentMapper().selectList(new LambdaQueryWrapper<CmsContent>()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getId, id));
        if (rows.isEmpty()) {
            return null;
        }
        ContentRow row = toRow(rows.get(0));
        rowCache.put(id, row);
        return row;
    }

    private List<ContentRow> findRows(Collection<Long> ids) {
        List<Long> missing = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && id > 0 && !rowCache.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            List<CmsContent> rows = mappers.contentMapper().selectList(new LambdaQueryWrapper<CmsContent>()
                    .eq(CmsContent::getSiteId, siteId)
                    .in(CmsContent::getId, missing));
            for (CmsContent row : rows) {
                ContentRow target = toRow(row);
                rowCache.put(target.idOrZero(), target);
            }
        }
        List<ContentRow> result = new ArrayList<>();
        for (Long id : ids) {
            ContentRow row = id == null ? null : rowCache.get(id);
            if (row != null) {
                result.add(row);
            }
        }
        return result;
    }

    private ContentRow findAuthorBySlug(String slug) {
        List<CmsContent> rows = mappers.contentMapper().selectList(new LambdaQueryWrapper<CmsContent>()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getTypeCode, "author")
                .eq(CmsContent::getSlug, slug)
                .orderByAsc(CmsContent::getId));
        return rows.isEmpty() ? null : toRow(rows.get(0));
    }

    /**
     * 实体 → 行。站点隔离在数据层是硬要求，因此所有单条查询都显式带 {@code site_id}
     * （逻辑删除的谓词由 {@code @TableLogic} 拼）。
     */
    private ContentRow toRow(CmsContent row) {
        ContentRow target = new ContentRow();
        target.setId(row.getId());
        target.setSiteId(row.getSiteId());
        target.setTypeCode(row.getTypeCode());
        target.setParentId(row.getParentId());
        target.setSlug(row.getSlug());
        target.setTitle(row.getTitle());
        target.setSummary(row.getSummary());
        target.setCover(row.getCover());
        target.setStatus(row.getStatus());
        target.setSort(row.getSort());
        target.setTop(row.getTop());
        target.setRecommend(row.getRecommend());
        target.setPublishTime(row.getPublishTime());
        target.setAuthorId(row.getAuthorId());
        target.setAuthorName(row.getAuthorName());
        target.setViewCount(row.getViewCount());
        target.setContentFormat(row.getContentFormat());
        target.setSeoTitle(row.getSeoTitle());
        target.setSeoDescription(row.getSeoDescription());
        target.setSeoKeywords(row.getSeoKeywords());
        target.setData(row.getData());
        target.setContent(row.getContent());
        target.setContentHtml(row.getContentHtml());
        target.setContentToc(row.getContentToc());
        target.setWordCount(row.getWordCount());
        target.setExpireTime(row.getExpireTime());
        target.setViewCountDay(row.getViewCountDay());
        target.setViewCountWeek(row.getViewCountWeek());
        target.setCommentCount(row.getCommentCount());
        target.setRatingAvg(row.getRatingAvg());
        target.setRatingCount(row.getRatingCount());
        target.setCreateTime(row.getCreateTime());
        target.setUpdateTime(row.getUpdateTime());
        return target;
    }

    /* ================= 列表查询（§6.3） ================= */

    @Override
    public QueryResult query(ContentQuery query) {
        ProviderParams params = params(query, true);
        List<ContentRow> rows = mappers.contentMapper().selectPublishRows(params);
        long total = params.getLimit() > 0
                ? mappers.contentMapper().countPublishRows(params)
                : rows.size();
        return new QueryResult(itemList(rows, params.getDepth(), params.getTypeCode()), total,
                params.getLimit());
    }

    @Override
    public long count(ContentQuery query) {
        return mappers.contentMapper().countPublishRows(params(query, false));
    }

    @Override
    public List<ContentItem> children(long parentId, String typeCode, int depth) {
        ProviderParams params = baseParams();
        boolean cross = typeCode == null || "all".equals(typeCode);
        params.setTypeAll(cross);
        params.setTypeCode(cross ? null : typeCode);
        params.setParentIds(List.of(parentId));
        params.setDepth(Math.max(depth, 1));
        // 扁平返回 depth 层以内的全部后代（顺序由 SQL 给：sort asc, id asc）；标签自己组树
        return plainItems(mappers.contentMapper().selectDescendants(params), params.getTypeCode());
    }

    @Override
    public List<ContentItem> contentAncestors(long contentId) {
        return plainItems(mappers.contentMapper().selectContentAncestors(siteId, contentId), null);
    }

    /* ================= 导航来源（§6.4） ================= */

    @Override
    public List<NavItem> categories(Long parentId, int depth, CountScope countScope) {
        long start = parentId == null ? 0L : parentId;
        return categoryLevel(start, Math.max(depth, 1), countScope);
    }

    private List<NavItem> categoryLevel(long parentId, int depth, CountScope countScope) {
        List<NavItem> items = new ArrayList<>();
        for (CmsCategory node : categoriesOf(parentId)) {
            items.add(categoryNode(node, depth, countScope));
        }
        return items;
    }

    private NavItem categoryNode(CmsCategory node, int depth, CountScope countScope) {
        NavItem item = categoryItem(node, countScope);
        if (depth <= 1) {
            return item;
        }
        List<NavItem> children = categoryLevel(node.getId(), depth - 1, countScope);
        return children.isEmpty() ? item : item.with("children", children);
    }

    @Override
    public NavItem category(String idOrSlug) {
        CmsCategory node = resolveCategory(idOrSlug);
        // 单个节点的 count 只算本节点（CountScope.node）；tree 口径由 categories() 给
        return node == null ? null : categoryItem(node, CountScope.node);
    }

    @Override
    public List<NavItem> categoryAncestors(long categoryId) {
        List<NavItem> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long cursor = categoryId;
        while (cursor != null && cursor > 0 && seen.add(cursor)) {
            CmsCategory node = categoryById().get(cursor);
            if (node == null) {
                break;
            }
            chain.add(categoryItem(node, CountScope.node));
            cursor = node.getParentId();
        }
        // 自下而上收集，输出反转成"从顶到下"（面包屑与 categoryPath 都按这个顺序读）
        java.util.Collections.reverse(chain);
        return chain;
    }

    /** 一个分类节点（§6.4 的产出：id / name / label / slug / url / path / count）。 */
    private NavItem categoryItem(CmsCategory node, CountScope countScope) {
        String path = categoryPath(node.getId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", node.getId());
        values.put("name", node.getName());
        values.put("label", node.getName());
        values.put("slug", node.getSlug());
        values.put("url", categoryUrl(path));
        values.put("path", path);
        values.put("count", categoryCount(node.getId(), countScope));
        values.put("sort", node.getSort() == null ? 0 : node.getSort());
        values.put("parentId", node.getParentId() == null ? 0L : node.getParentId());
        return NavItem.of(values);
    }

    @Override
    public List<NavItem> typeNodes() {
        List<NavItem> items = new ArrayList<>();
        Map<String, Long> counts = new HashMap<>();
        for (Rows.TypeCountRow row : mappers.contentMapper().selectTypeCounts(baseParams())) {
            counts.put(row.getTypeCode(), row.getCount() == null ? 0L : row.getCount());
        }
        typeMap();
        for (ContentTypeDef def : typeMap().values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", def.id());
            values.put("name", def.name());
            values.put("label", def.name());
            values.put("slug", def.code());
            values.put("typeCode", def.code());
            values.put("url", typeListUrl(def));
            values.put("count", counts.getOrDefault(def.code(), 0L));
            values.put("sort", typeSort == null ? 0 : typeSort.getOrDefault(def.code(), 0));
            items.add(NavItem.of(values));
        }
        return items;
    }

    @Override
    public List<String> menuCodes() {
        List<CmsMenu> rows = mappers.menuMapper().selectList(new LambdaQueryWrapper<CmsMenu>()
                .eq(CmsMenu::getSiteId, siteId)
                .eq(CmsMenu::getStatus, 1)
                .orderByAsc(CmsMenu::getSort)
                .orderByAsc(CmsMenu::getId));
        List<String> codes = new ArrayList<>(rows.size());
        for (CmsMenu row : rows) {
            codes.add(row.getCode());
        }
        return codes;
    }

    @Override
    public List<NavItem> menu(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        List<CmsMenu> menus = mappers.menuMapper().selectList(new LambdaQueryWrapper<CmsMenu>()
                .eq(CmsMenu::getSiteId, siteId)
                .eq(CmsMenu::getCode, code)
                .eq(CmsMenu::getStatus, 1)
                .orderByAsc(CmsMenu::getId));
        if (menus.isEmpty()) {
            return List.of();
        }
        long menuId = menus.get(0).getId();
        List<CmsMenuItem> items = mappers.menuItemMapper().selectList(
                new LambdaQueryWrapper<CmsMenuItem>()
                        .eq(CmsMenuItem::getMenuId, menuId)
                        .orderByAsc(CmsMenuItem::getSort)
                        .orderByAsc(CmsMenuItem::getId));
        return buildMenuLevel(items, 0L);
    }

    /** 菜单项树（§2.4）：{@code label} 为空时取所指向对象的名称，{@code url} 按 kind 现算。 */
    private List<NavItem> buildMenuLevel(List<CmsMenuItem> all, long parentId) {
        List<NavItem> level = new ArrayList<>();
        for (CmsMenuItem row : all) {
            long parent = row.getParentId() == null ? 0L : row.getParentId();
            if (parent != parentId || !flag(row.getVisible())) {
                continue;
            }
            level.add(menuItem(row, buildMenuLevel(all, row.getId())));
        }
        return level;
    }

    private NavItem menuItem(CmsMenuItem row, List<NavItem> children) {
        String kind = row.getKind() == null ? "custom" : row.getKind();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", row.getId());
        values.put("kind", kind);
        values.put("refId", row.getRefId() == null ? 0L : row.getRefId());
        values.put("refCode", row.getRefCode());
        values.put("target", row.getTarget() == null ? "" : row.getTarget());
        values.put("rel", row.getRel() == null ? "" : row.getRel());
        values.put("sort", row.getSort() == null ? 0 : row.getSort());
        values.put("url", menuUrl(row, kind));
        Object label = menuLabel(row, kind);
        values.put("label", label);
        values.put("name", label);
        values.put("slug", row.getRefCode() == null ? values.get("url") : row.getRefCode());
        if (!children.isEmpty()) {
            values.put("children", children);
        }
        return NavItem.of(values);
    }

    /** §2.4：菜单项指向什么就取什么的 URL；{@code url} / {@code custom} 原样透传。 */
    private String menuUrl(CmsMenuItem row, String kind) {
        Long refId = row.getRefId();
        switch (kind) {
            case "category" -> {
                CmsCategory category = refId == null ? null : categoryById().get(refId);
                return category == null ? "" : categoryUrl(categoryPath(category.getId()));
            }
            case "content" -> {
                ContentRow content = refId == null ? null : findRow(refId);
                return content == null ? "" : detailUrl(content, null);
            }
            case "type" -> {
                ContentTypeDef def = type(row.getRefCode());
                return def == null ? "" : typeListUrl(def);
            }
            case "tag" -> {
                CmsTag tag = refId == null ? null : tagById().get(refId);
                return tag == null ? "" : tagUrl(tag.getSlug());
            }
            case "archive" -> {
                String ref = row.getRefCode() == null ? String.valueOf(refId) : row.getRefCode().trim();
                String[] parts = ref.split("-");
                try {
                    int year = Integer.parseInt(parts[0]);
                    Integer month = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
                    return archiveUrl(year, month);
                } catch (NumberFormatException e) {
                    return "";   // ref 不是 2026 / 2026-03 这种形态：不猜
                }
            }
            case "author" -> {
                ContentRow author = refId == null ? null : findRow(refId);
                return author == null ? "" : detailUrl(author, null);
            }
            default -> {
                return row.getUrl() == null ? "" : row.getUrl();
            }
        }
    }

    private Object menuLabel(CmsMenuItem row, String kind) {
        if (row.getLabel() != null && !row.getLabel().isBlank()) {
            return row.getLabel();
        }
        Long refId = row.getRefId();
        switch (kind) {
            case "category" -> {
                CmsCategory category = refId == null ? null : categoryById().get(refId);
                return category == null ? "" : category.getName();
            }
            case "content", "author" -> {
                ContentRow content = refId == null ? null : findRow(refId);
                return content == null ? "" : content.getTitle();
            }
            case "type" -> {
                ContentTypeDef def = type(row.getRefCode());
                return def == null ? "" : def.name();
            }
            case "tag" -> {
                CmsTag tag = refId == null ? null : tagById().get(refId);
                return tag == null ? "" : tag.getName();
            }
            default -> {
                return row.getRefCode() == null ? "" : row.getRefCode();
            }
        }
    }

    @Override
    public List<NavItem> facetValues(String typeCode, String fieldCode) {
        ContentTypeDef def = type(typeCode);
        if (def == null || fieldCode == null) {
            return List.of();
        }
        ProviderParams params = baseParams();
        params.setTypeCode(typeCode);
        params.setTypeAll(false);
        int cardinality = numberOption("facets.cardinality", 50);
        List<Rows.FacetCountRow> rows = mappers.contentMapper()
                .selectFacetCounts(params, fieldCode, cardinality);
        FieldDef field = def.field(fieldCode);
        Map<String, String> labels = new LinkedHashMap<>();
        if (field != null) {
            for (EnumOption option : field.options()) {
                labels.put(option.value(), option.label());
            }
        }
        List<NavItem> items = new ArrayList<>(rows.size());
        for (Rows.FacetCountRow row : rows) {
            String value = row.getValue();
            long count = row.getCount() == null ? 0L : row.getCount();
            if (value == null || value.isBlank() || count <= 0) {
                continue;
            }
            String facetPath = fieldCode + "-" + value;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("value", value);
            values.put("slug", value);
            values.put("label", labels.getOrDefault(value, value));
            values.put("name", labels.getOrDefault(value, value));
            values.put("facetPath", facetPath);
            values.put("url", facetUrl(facetPath));
            values.put("count", count);
            items.add(NavItem.of(values));
        }
        return items;
    }

    /* ================= 标签与归档（§6.4） ================= */

    @Override
    public List<NavItem> tags(String typeCode, int row, String orderby, int minCount) {
        boolean all = typeCode == null || "all".equals(typeCode);
        ProviderParams params = baseParams();
        params.setTypeAll(all);
        params.setTypeCode(all ? null : typeCode);
        List<Rows.TagCountRow> rows = mappers.contentTagMapper().selectTagCounts(
                params, orderby == null || orderby.isBlank() ? "count" : orderby,
                Math.max(minCount, 0), Math.max(row, 0));
        List<NavItem> items = new ArrayList<>(rows.size());
        for (Rows.TagCountRow tag : rows) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", tag.getId());
            values.put("name", tag.getName());
            values.put("label", tag.getName());
            values.put("slug", tag.getSlug());
            values.put("url", tagUrl(tag.getSlug()));
            values.put("count", tag.getCount() == null ? 0L : tag.getCount());
            items.add(NavItem.of(values));
        }
        return items;
    }

    @Override
    public NavItem tag(String slug) {
        CmsTag tag = resolveTag(slug);
        if (tag == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", tag.getId());
        values.put("name", tag.getName());
        values.put("label", tag.getName());
        values.put("slug", tag.getSlug());
        values.put("url", tagUrl(tag.getSlug()));
        values.put("count", tagContentCount(tag.getId()));
        return NavItem.of(values);
    }

    @Override
    public List<NavItem> archives(String typeCode, String mode, int row, String category,
                                  boolean includeChildren) {
        boolean all = typeCode == null || "all".equals(typeCode);
        ProviderParams params = baseParams();
        params.setTypeAll(all);
        params.setTypeCode(all ? null : typeCode);
        params.setCategoryIds(resolveCategoryIds(category, includeChildren));
        params.setLimit(Math.max(row, 0));
        List<Rows.ArchiveCountRow> rows = mappers.contentMapper()
                .selectArchiveCounts(params, "year".equalsIgnoreCase(mode) ? "year" : "month");
        List<NavItem> items = new ArrayList<>(rows.size());
        for (Rows.ArchiveCountRow group : rows) {
            int year = group.getYear() == null ? 0 : group.getYear();
            int month = group.getMonth() == null ? 0 : group.getMonth();
            // 数据层给的是 URL 用的形态（2026-03 / 2026）；"2026 年 3 月"由 ArchiveTag 现算（§6.4）
            String label = month > 0 ? String.format("%04d-%02d", year, month) : String.valueOf(year);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("year", year);
            values.put("month", month);
            values.put("label", label);
            values.put("url", archiveUrl(year, month > 0 ? month : null));
            values.put("count", group.getCount() == null ? 0L : group.getCount());
            items.add(NavItem.of(values));
        }
        return items;
    }

    /* ================= 邻接（§6.4） ================= */

    @Override
    public Neighbors neighbors(String typeCode, long anchorId, String within, String category) {
        if (anchorId <= 0) {
            return Neighbors.NONE;
        }
        ContentRow anchor = findRow(anchorId);
        if (anchor == null) {
            return Neighbors.NONE;
        }
        boolean all = typeCode == null || "all".equals(typeCode);
        ProviderParams params = baseParams();
        params.setTypeAll(all);
        params.setTypeCode(all ? null : typeCode);
        params.setCategoryIds(resolveCategoryIds(category, true));
        params.setAnchorId(anchorId);
        params.setAnchorParentId(anchor.parentIdOrZero());
        if ("parent".equals(within)) {
            // within='parent'：只与"同一个父"的子内容相邻（§6.4）——锚定项的父的子内容
            params.setOf("parent");
        }
        params.setOrder(orderedByParams(defaultOrder(typeCode), params.getTypeCode()));
        List<ContentRow> prev = mappers.contentMapper().selectPrevNeighbor(params);
        List<ContentRow> next = mappers.contentMapper().selectNextNeighbor(params);
        return new Neighbors(prev.isEmpty() ? null : plainItems(prev, params.getTypeCode()).get(0),
                next.isEmpty() ? null : plainItems(next, params.getTypeCode()).get(0));
    }

    /* ================= 条件翻译（ContentQuery → ProviderParams） ================= */

    private ProviderParams baseParams() {
        ProviderParams params = new ProviderParams();
        params.setSiteId(siteId);
        return params;
    }

    /**
     * {@code ContentQuery} → SQL 条件（§6.3 的共用参数表）。
     *
     * @param paged {@code false} 时忽略 {@code row} / {@code offset}（{@link #count} 用）
     */
    private ProviderParams params(ContentQuery query, boolean paged) {
        ProviderParams params = baseParams();
        boolean crossType = query.crossType() || query.typeCode() == null;
        params.setTypeAll(crossType);
        params.setTypeCode(crossType ? null : query.typeCode());
        params.setCategoryIds(resolveCategoryIds(query.category(), query.includeChildren()));

        params.setTagFilterActive(!query.tags().isEmpty());
        List<Long> tagIds = new ArrayList<>();
        boolean missing = false;
        for (String token : query.tags()) {
            Long id = resolveTagId(token);
            if (id == null) {
                missing = true;
            } else {
                tagIds.add(id);
            }
        }
        params.setTagIds(tagIds);
        params.setTagMissing(missing);

        String author = query.author();
        if (author == null || "all".equals(author)) {
            params.setAuthorId(null);
        } else {
            Long authorId = resolveAuthorId(author);
            params.setAuthorId(authorId);
            params.setAuthorMissing(authorId == null);
        }

        params.setOf(query.of());
        String relate = query.relate();
        if ("tag".equals(relate) || "category".equals(relate)) {
            params.setRelate(relate);
        } else if (relate != null && relate.startsWith("field:")) {
            params.setRelateField(relate.substring("field:".length()));
        }
        params.setAnchorId(query.anchorId());
        params.setAnchorParentId(query.anchorParentId());
        params.setExcludeSelf(query.excludeSelf());
        params.setConditions(conditions(query.where()));
        params.setKeyword(query.keyword() == null || query.keyword().isBlank() ? null : query.keyword());
        params.setTop(query.top());
        params.setRecommend(query.recommend());

        String status = query.status();
        if (status == null || status.isBlank() || "PUBLISHED".equalsIgnoreCase(status)) {
            params.setStatusMode("published");
        } else if ("any".equalsIgnoreCase(status)) {
            params.setStatusMode("any");
        } else {
            params.setStatusMode("exact");
            params.setStatusValue(status);
        }

        params.setRelationOrder(query.relationOrder() && params.getRelateField() != null);
        params.setOrder(orderedByParams(query.orderby(), params.getTypeCode()));
        params.setDepth(Math.max(query.depth(), 1));
        if (paged) {
            params.setLimit(Math.max(query.row(), 0));
            params.setOffset(Math.max(query.offset(), 0));
        }
        return params;
    }

    /** {@code where} 的每个条件：先判它落在哪里（关联表 / 内置列 / 索引表），再判取值类型。 */
    private List<ProviderParams.Condition> conditions(List<WhereCondition> where) {
        List<ProviderParams.Condition> conditions = new ArrayList<>();
        for (WhereCondition condition : where) {
            ProviderParams.Condition target = new ProviderParams.Condition();
            String code = condition.fieldCode();
            target.setFieldCode(code);
            target.setOp(condition.op() == null ? "eq" : condition.op().name());
            target.setValues(condition.values());
            target.setKind(conditionKind(code));
            target.setValueKind(valueKind(target.getFirst()));
            conditions.add(target);
        }
        return conditions;
    }

    /**
     * {@code where} 的字段落在哪里：内置字段按 §2.2 的白名单分派（文本 / 数字 / 时间），
     * {@code categoryId} / {@code tagId} 在关联表，其余（{@code indexed} 的自定义字段）在索引表。
     */
    private static String conditionKind(String code) {
        if ("categoryId".equals(code)) {
            return "category";
        }
        if ("tagId".equals(code)) {
            return "tag";
        }
        return switch (code) {
            case "slug", "typeCode", "status" -> "text";
            case "publishTime", "expireTime", "updateTime" -> "time";
            case "id", "sort", "top", "recommend", "authorId", "parentId", "viewCount", "viewCountDay",
                    "viewCountWeek", "commentCount", "wordCount" -> "number";
            // 白名单外的 code 一律按"自定义字段走索引表"——查不到就是不匹配（编译期已经报过 E2007）
            default -> "custom";
        };
    }

    /** 一个取值按"数字 / 时间 / 文本"归类，决定自定义字段比索引表的哪一列（§2.5）。 */
    static String valueKind(String text) {
        if (text == null || text.isBlank()) {
            return "str";
        }
        String value = text.trim();
        if (value.matches("[-+]?\\d+(\\.\\d+)?")) {
            return "num";
        }
        if (parseTime(value) != null) {
            return "time";
        }
        return "str";
    }

    /** 排序项：内置字段按 §2.2 的 {@code orderby} 白名单，其余按 {@code indexed} 自定义字段。 */
    private List<ProviderParams.OrderItem> orderedByParams(List<OrderBy> orderby, String typeCode) {
        List<ProviderParams.OrderItem> result = new ArrayList<>();
        for (OrderBy order : orderby) {
            result.add(orderItem(order.fieldCode(), order.desc(), typeCode));
        }
        return result;
    }

    private ProviderParams.OrderItem orderItem(String fieldCode, boolean desc, String typeCode) {
        ProviderParams.OrderItem item = new ProviderParams.OrderItem();
        item.setFieldCode(fieldCode);
        item.setDesc(desc);
        if (BuiltinFields.orderbyCapable(fieldCode)) {
            item.setKind("builtin");
            return item;
        }
        item.setKind("custom");
        item.setValueKind(valueKindForField(typeCode, fieldCode));
        return item;
    }

    /** 自定义字段比索引表的哪一列，由字段定义的类型决定（§2.2）。 */
    private String valueKindForField(String typeCode, String fieldCode) {
        FieldDef field = field(typeCode, fieldCode);
        if (field == null) {
            return "str";
        }
        FieldType type = field.fieldType();
        if (type == FieldType.INT || type == FieldType.DECIMAL) {
            return "num";
        }
        if (type == FieldType.DATE || type == FieldType.DATETIME) {
            return "time";
        }
        return "str";
    }

    private FieldDef field(String typeCode, String fieldCode) {
        if (typeCode != null) {
            ContentTypeDef def = type(typeCode);
            return def == null ? null : def.field(fieldCode);
        }
        for (ContentTypeDef def : typeMap().values()) {
            FieldDef field = def.field(fieldCode);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /** 类型的默认排序（{@code sort_field} / {@code sort_order}，未配时 {@code publishTime desc}，§6.3）。 */
    private List<OrderBy> defaultOrder(String typeCode) {
        ContentTypeDef def = typeCode == null || "all".equals(typeCode) ? null : type(typeCode);
        if (def == null) {
            return List.of(new OrderBy("publishTime", true));
        }
        boolean desc = def.sortOrder() == null || !"asc".equalsIgnoreCase(def.sortOrder());
        return List.of(new OrderBy(def.sortFieldOrDefault(), desc));
    }

    /** 栏目参数（id 或 slug）→ 分类 id 集合；{@code all} / null = 不限（返回 null）。 */
    private List<Long> resolveCategoryIds(String category, boolean includeChildren) {
        if (category == null || category.isBlank() || "all".equals(category)) {
            return null;
        }
        CmsCategory node = resolveCategory(category);
        if (node == null) {
            return List.of();   // 解析不出来的分类：一条都不匹配，而不是"不限"
        }
        return includeChildren ? subtreeCategoryIds(node.getId()) : List.of(node.getId());
    }

    /** {@code author} 参数 → 作者 id（{@code self} 在标签层已经换成 authorId，这里只认 id / slug）。 */
    private Long resolveAuthorId(String author) {
        String text = author.trim();
        Long id = asLong(text);
        if (id != null) {
            return id;
        }
        ContentRow row = findAuthorBySlug(text);
        return row == null ? null : row.idOrZero();
    }

    /* ================= 内容项的组装（§2.2 的派生字段） ================= */

    /** 扁平组装（{@code children} / {@code contentAncestors} / 邻接）：不带 {@code children} 预加载。 */
    private List<ContentItem> plainItems(List<ContentRow> rows, String typeCode) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Batch batch = new Batch(rows);
        batch.load(typeCode);
        List<ContentItem> items = new ArrayList<>(rows.size());
        for (ContentRow row : rows) {
            items.add(ContentItem.of(values(row, batch)));
        }
        return items;
    }

    /**
     * 组装一页内容；{@code depth > 1} 时每项预加载 {@code children}（§6.3 的 {@code depth}）。
     *
     * <p>后代一次查出来（一次 SQL），与当页内容**共用一个 Batch**——否则树里每个节点都会各查一遍
     * 自己的分类 / 标签 / 父内容。
     */
    private List<ContentItem> itemList(List<ContentRow> rows, int depth, String typeCode) {
        if (rows.isEmpty()) {
            return List.of();
        }
        if (depth <= 1) {
            return plainItems(rows, typeCode);
        }
        List<Long> ids = new ArrayList<>(rows.size());
        for (ContentRow row : rows) {
            ids.add(row.idOrZero());
        }
        ProviderParams params = baseParams();
        boolean cross = typeCode == null || "all".equals(typeCode);
        params.setTypeAll(cross);
        params.setTypeCode(cross ? null : typeCode);
        params.setParentIds(ids);
        params.setDepth(depth);
        List<ContentRow> descendants = mappers.contentMapper().selectDescendants(params);
        List<ContentRow> all = new ArrayList<>(rows);
        all.addAll(descendants);
        Batch batch = new Batch(all);
        batch.load(typeCode);
        Map<Long, List<ContentRow>> byParent = new LinkedHashMap<>();
        for (ContentRow row : descendants) {
            byParent.computeIfAbsent(row.parentIdOrZero(), key -> new ArrayList<>()).add(row);
        }
        return treeItems(rows, batch, byParent, depth - 1);
    }

    private List<ContentItem> treeItems(List<ContentRow> rows, Batch batch,
                                        Map<Long, List<ContentRow>> byParent, int levels) {
        List<ContentItem> items = new ArrayList<>(rows.size());
        for (ContentRow row : rows) {
            ContentItem item = ContentItem.of(values(row, batch));
            if (levels > 0) {
                List<ContentItem> children = treeItems(
                        byParent.getOrDefault(row.idOrZero(), List.of()), batch, byParent, levels - 1);
                if (!children.isEmpty()) {
                    item = ContentItem.of(withChildren(item, children));
                }
            }
            items.add(item);
        }
        return items;
    }

    private static Map<String, Object> withChildren(ContentItem item, List<ContentItem> children) {
        Map<String, Object> values = item.mutableValues();
        values.put("children", children);
        return values;
    }

    private ContentItem item(ContentRow row, int depth, String typeCode) {
        return itemList(List.of(row), depth, typeCode).get(0);
    }

    /**
     * 一条内容的全部取值：内置列 + 自定义字段（jsonb）+ 派生字段（§2.2）。
     *
     * <p>{@code current} / {@code class} **不在这里**——它们依赖"这一页是谁"，由渲染层在压栈时补（§5.5）。
     */
    private Map<String, Object> values(ContentRow row, Batch batch) {
        Map<String, Object> values = new LinkedHashMap<>();
        ContentTypeDef def = type(row.getTypeCode());
        values.put("id", row.idOrZero());
        values.put("typeCode", row.getTypeCode());
        values.put("typeName", def == null ? row.getTypeCode() : def.name());
        values.put("parentId", row.parentIdOrZero());
        values.put("slug", row.getSlug());
        values.put("title", row.getTitle());
        values.put("summary", row.getSummary());
        values.put("cover", cover(row));
        values.put("status", row.getStatus());
        values.put("sort", row.getSort() == null ? 0 : row.getSort());
        values.put("top", row.getTop() == null ? 0 : row.getTop());
        values.put("recommend", row.getRecommend() == null ? 0 : row.getRecommend());
        values.put("publishTime", row.getPublishTime());
        values.put("expireTime", row.getExpireTime());
        values.put("authorId", row.getAuthorId());
        values.put("authorName", row.getAuthorName());
        values.put("viewCount", row.getViewCount() == null ? 0L : row.getViewCount());
        values.put("viewCountDay", row.getViewCountDay() == null ? 0L : row.getViewCountDay());
        values.put("viewCountWeek", row.getViewCountWeek() == null ? 0L : row.getViewCountWeek());
        values.put("commentCount", row.getCommentCount() == null ? 0 : row.getCommentCount());
        values.put("ratingAvg", row.getRatingAvg() == null ? BigDecimal.ZERO : row.getRatingAvg());
        values.put("ratingCount", row.getRatingCount() == null ? 0 : row.getRatingCount());
        values.put("contentFormat", row.getContentFormat());
        values.put("seoTitle", row.getSeoTitle());
        values.put("seoDescription", row.getSeoDescription());
        values.put("seoKeywords", row.getSeoKeywords());
        values.put("content", bodyOf(row));
        values.put("contentHtml", bodyOf(row));
        values.put("createTime", row.getCreateTime());
        values.put("updateTime", row.getUpdateTime());

        // ---- 自定义字段（jsonb 的 data）----
        Map<String, Object> data = Json.object(row.getData());
        if (def != null) {
            for (FieldDef field : def.fields()) {
                Object raw = data.get(field.code());
                if (raw == null) {
                    // 声明过的字段"存在但为空"：多值给空表、标量给 null。
                    // 这样 [field:x/] 输出空串、[field:x.count/] 是 0，而不是渲染期报"找不到"
                    values.put(field.code(), field.fieldType().multiValued() ? List.of() : null);
                    continue;
                }
                values.put(field.code(), fieldValue(field, raw, batch));
            }
        }

        // ---- 派生字段（§2.2）----
        applyDerived(row, values, batch);
        return values;
    }

    private String cover(ContentRow row) {
        if (row.getCover() != null && !row.getCover().isBlank()) {
            return row.getCover();
        }
        String fallback = site().defaultCover();
        return fallback == null || fallback.isBlank() ? row.getCover() : fallback;
    }

    /** 正文：两类正文都以 {@code content_html} 为渲染源（§5.2.2），没有预渲染结果时用原文。 */
    private static String bodyOf(ContentRow row) {
        if (row.getContentHtml() != null && !row.getContentHtml().isBlank()) {
            return row.getContentHtml();
        }
        return row.getContent();
    }

    /** 一条内容不依赖页面上下文的派生字段（§2.2、§6.3）。 */
    private void applyDerived(ContentRow row, Map<String, Object> values, Batch batch) {
        values.put("url", detailUrl(row, batch));
        values.put("canonical", site().url() + values.get("url"));

        ContentRow parent = row.parentIdOrZero() > 0 ? batch.parents.get(row.parentIdOrZero()) : null;
        values.put("parentTitle", parent == null ? null : parent.getTitle());
        values.put("parentSlug", parent == null ? null : parent.getSlug());
        values.put("parentUrl", parent == null ? null : detailUrl(parent, batch));
        values.put("parentTypeCode", parent == null ? null : parent.getTypeCode());

        List<ContentItem> ancestorItems = new ArrayList<>();
        for (ContentRow ancestor : batch.ancestors(row)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", ancestor.idOrZero());
            entry.put("typeCode", ancestor.getTypeCode());
            entry.put("slug", ancestor.getSlug());
            entry.put("title", ancestor.getTitle());
            entry.put("url", detailUrl(ancestor, batch));
            ancestorItems.add(ContentItem.of(entry));
        }
        values.put("ancestors", ancestorItems);

        long childCount = batch.childCounts.getOrDefault(row.idOrZero(), 0L);
        values.put("childCount", childCount);
        values.put("hasChildren", childCount > 0);

        // 分类：主分类（dimension='primary'）排第一（§2.3：它决定 categoryUrl / 面包屑 / canonical）
        List<Map<String, Object>> categories = new ArrayList<>();
        List<Long> categoryIds = new ArrayList<>();
        for (CmsCategory node : batch.categoriesOf(row.idOrZero())) {
            String path = categoryPath(node.getId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", node.getId());
            entry.put("name", node.getName());
            entry.put("slug", node.getSlug());
            entry.put("url", categoryUrl(path));
            entry.put("path", path);
            entry.put("dimension", batch.dimensionOf(row.idOrZero(), node.getId()));
            categories.add(entry);
            categoryIds.add(node.getId());
        }
        values.put("categories", categories);
        values.put("categoryId", categoryIds.isEmpty() ? null : categoryIds);
        Map<String, Object> primary = categories.isEmpty() ? null : categories.get(0);
        values.put("categoryName", primary == null ? null : primary.get("name"));
        values.put("categoryPath", primary == null ? null : primary.get("path"));
        values.put("categoryUrl", primary == null ? null : primary.get("url"));

        List<Map<String, Object>> tags = new ArrayList<>();
        for (CmsTag tag : batch.tagsOf(row.idOrZero())) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", tag.getId());
            entry.put("name", tag.getName());
            entry.put("slug", tag.getSlug());
            entry.put("url", tagUrl(tag.getSlug()));
            entry.put("count", tagContentCount(tag.getId()));
            tags.add(entry);
        }
        values.put("tags", tags);
        values.put("tagId", batch.tagIdsOf(row.idOrZero()));

        // 作者：author_id 指向 type_code='author' 的内容项（§2.3），authorUrl 是它的详情页
        ContentRow author = row.getAuthorId() == null ? null : batch.referenced.get(row.getAuthorId());
        values.put("authorUrl", author == null ? null : detailUrl(author, batch));
        values.put("authorAvatar", author == null ? null : authorAvatar(author, batch));

        // 正文计量：word_count 列优先，空则按纯文本正文字数兜底（§2.2）
        int wordCount = row.getWordCount() == null ? 0 : row.getWordCount();
        String body = bodyOf(row);
        if (wordCount <= 0 && body != null) {
            wordCount = plainLength(body);
        }
        values.put("wordCount", wordCount);
        int speed = numberOption("reading.speed", 400);
        values.put("readingMinutes",
                Math.max(1, (int) Math.round(wordCount / (double) Math.max(speed, 1))));
        values.put("imageCount", body == null ? 0 : countOccurrences(body, "<img"));

        List<Map<String, Object>> toc = tocOf(row.getContentToc());
        values.put("toc", toc.isEmpty() ? null : toc);

        values.put("updatedDaysAgo", row.getUpdateTime() == null ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(row.getUpdateTime(), LocalDateTime.now())));
    }

    /** {@code content_toc} 是"正文标题树"（§5.2.5）；数组直接给，包在对象里时按常见 key 拆一层。 */
    static List<Map<String, Object>> tocOf(String json) {
        Object parsed = Json.parse(json);
        List<Map<String, Object>> items = Json.asObjectList(parsed);
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> wrapper = Json.asObject(parsed);
        for (String key : List.of("items", "list", "toc", "children", "headings")) {
            List<Map<String, Object>> nested = Json.asObjectList(wrapper.get(key));
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return List.of();
    }

    /** 作者的 {@code avatar} 自定义字段；没有就退回它的封面（§2.2 的 {@code authorAvatar}）。 */
    private Object authorAvatar(ContentRow author, Batch batch) {
        Map<String, Object> data = Json.object(author.getData());
        Object avatar = data.get("avatar");
        if (avatar == null) {
            avatar = data.get("headImg");
        }
        if (avatar == null) {
            return author.getCover();
        }
        ContentTypeDef def = type(author.getTypeCode());
        FieldDef field = def == null ? null : def.field("avatar");
        return field == null ? avatar : fieldValue(field, avatar, batch);
    }

    /** 一个自定义字段值 → 引擎的 formatter / foreach 能直接消费的形态（§2.2 的"存储"列）。 */
    private Object fieldValue(FieldDef field, Object raw, Batch batch) {
        FieldType type = field.fieldType();
        if (raw == null) {
            return type.multiValued() ? List.of() : null;
        }
        switch (type) {
            case INT, DECIMAL -> {
                return number(raw);
            }
            case BOOL -> {
                return bool(raw);
            }
            case DATE, DATETIME -> {
                LocalDateTime time = raw instanceof LocalDateTime value
                        ? value : parseTime(String.valueOf(raw));
                return time == null ? raw : time;
            }
            case ENUM -> {
                return text(raw);
            }
            case ENUM_MULTI -> {
                Map<String, String> labels = new LinkedHashMap<>();
                for (EnumOption option : field.options()) {
                    labels.put(option.value(), option.label());
                }
                List<Map<String, Object>> items = new ArrayList<>();
                for (String value : storedValues(raw)) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("value", value);
                    entry.put("label", labels.getOrDefault(value, value));
                    items.add(entry);
                }
                return items;
            }
            case IMAGE, FILE -> {
                return media(raw, batch, false);
            }
            case IMAGES, FILES -> {
                return media(raw, batch, true);
            }
            case TAGS -> {
                return tagValues(raw);
            }
            case RELATION -> {
                return relatedValues(raw, batch);
            }
            case JSON -> {
                return raw instanceof String text ? Json.parse(text) : normalize(raw);
            }
            default -> {
                return text(raw);
            }
        }
    }

    /** 媒体引用（media id / url / 已经是对象）→ Map（{@code url} / {@code alt} / {@code size}…）。 */
    private Object media(Object raw, Batch batch, boolean multi) {
        if (multi) {
            List<Object> values = new ArrayList<>();
            for (Object element : listOf(raw)) {
                Object media = media(element, batch, false);
                if (media != null) {
                    values.add(media);
                }
            }
            return values;
        }
        if (raw instanceof Map<?, ?> map) {
            return normalize(Json.asObject(map));
        }
        if (raw instanceof Number || isNumeric(text(raw))) {
            Long id = asLong(text(raw));
            CmsMedia found = id == null ? null : batch.media.get(id);
            return found == null ? raw : mediaMap(found);
        }
        // 已经是 URL（或任意字符串）：原样给模板，FieldOutput 输出 url 时正好是它
        return text(raw);
    }

    private Map<String, Object> mediaMap(CmsMedia media) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", media.getId());
        values.put("url", mediaUrl(media.getUrl()));
        values.put("name", media.getName());
        values.put("alt", media.getName());
        values.put("title", media.getName());
        values.put("ext", media.getExt());
        values.put("mime", media.getMimeType());
        values.put("size", media.getSize() == null ? 0L : media.getSize());
        return values;
    }

    /** 媒体 URL 加站点前缀（§2.7 的 {@code media.host}，空 = 站内 /uploads）。 */
    private String mediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String host = site().mediaHost();
        if (host == null || host.isBlank() || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return host.endsWith("/") ? host.substring(0, host.length() - 1) + url : host + url;
    }

    /** {@code TAGS} 字段：data 里可能是标签 id 也可能是 slug，两种都认（§2.2）。 */
    private List<Map<String, Object>> tagValues(Object raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String token : storedValues(raw)) {
            CmsTag tag = resolveTag(token);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", tag == null ? null : tag.getId());
            entry.put("name", tag == null ? token : tag.getName());
            entry.put("label", tag == null ? token : tag.getName());
            entry.put("slug", tag == null ? token : tag.getSlug());
            entry.put("url", tag == null ? tagUrl(token) : tagUrl(tag.getSlug()));
            entry.put("count", tag == null ? 0L : tagContentCount(tag.getId()));
            items.add(entry);
        }
        return items;
    }

    /**
     * {@code RELATION} 字段：把 id 数组换成被引用内容的摘要（id / title / slug / url / typeCode）。
     * 取不到的那条只留 id（跨站点 / 已删），列表本身不塌。
     */
    private List<Map<String, Object>> relatedValues(Object raw, Batch batch) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String token : storedValues(raw)) {
            Long id = asLong(token);
            if (id == null) {
                continue;
            }
            ContentRow row = batch.referenced.get(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            if (row != null) {
                entry.put("title", row.getTitle());
                entry.put("summary", row.getSummary());
                entry.put("slug", row.getSlug());
                entry.put("typeCode", row.getTypeCode());
                entry.put("url", detailUrl(row, batch));
                entry.put("publishTime", row.getPublishTime());
            }
            items.add(entry);
        }
        return items;
    }

    /* ================= URL（§7.1，唯一实现是 UrlPatternResolver） ================= */

    /** 详情页 URL：{@code detail_url_pattern} 填不出来时兜底 {@code /{typeCode}/{slug}.html}。 */
    private String detailUrl(ContentRow row, Batch batch) {
        ContentTypeDef def = type(row.getTypeCode());
        String pattern = detailPattern(def);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", row.idOrZero());
        values.put("slug", row.getSlug());
        values.put("typeCode", row.getTypeCode());
        values.put("sort", row.getSort());
        values.put("lang", site().lang());
        CmsCategory primary = batch == null ? primaryCategory(row.idOrZero())
                : batch.primaryCategory(row.idOrZero());
        if (primary != null) {
            String path = categoryPath(primary.getId());
            values.put("categoryPath", trimSlashes(path));
            values.put("categorySlug", primary.getSlug());
        }
        if (row.parentIdOrZero() > 0) {
            ContentRow parent = batch == null ? findRow(row.parentIdOrZero())
                    : batch.parents.get(row.parentIdOrZero());
            if (parent != null) {
                values.put("parentSlug", parent.getSlug());
            }
        }
        LocalDateTime publishTime = row.getPublishTime();
        if (publishTime != null) {
            values.put("year", publishTime.getYear());
            values.put("month", publishTime.getMonthValue());
            values.put("day", publishTime.getDayOfMonth());
        }
        String fallback = "/" + row.getTypeCode() + "/" + row.getSlug() + ".html";
        return resolveUrl(pattern, values, fallback);
    }

    /** §7.1：{@code detail_url_pattern} 为空（或类型不存在）时的兜底形态。 */
    static String detailPattern(ContentTypeDef def) {
        if (def == null || def.detailUrlPattern() == null || def.detailUrlPattern().isBlank()) {
            return FALLBACK_DETAIL_PATTERN;
        }
        return def.detailUrlPattern();
    }

    /** 分类索引页 URL：站点选项 {@code url.list}（默认 {@code /{categoryPath}/page-{n}/}）的第 1 页形态。 */
    private String categoryUrl(String categoryPath) {
        String pattern = option("url.list", "/{categoryPath}/page-{n}/");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("categoryPath", trimSlashes(categoryPath));
        values.put("categorySlug", lastSegment(categoryPath));
        values.put("lang", site().lang());
        return resolveUrl(pattern, values, categoryPath == null || categoryPath.isEmpty()
                ? "/" : categoryPath + "/");
    }

    /** 标签页 URL：站点选项 {@code url.tag}（默认 {@code /tag/{tagSlug}/}，§7.1.1）。 */
    private String tagUrl(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        String pattern = option("url.tag", "/tag/{tagSlug}/");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tagSlug", slug);
        values.put("lang", site().lang());
        return resolveUrl(pattern, values, "/tag/" + slug + "/");
    }

    /** 归档页 URL：站点选项 {@code url.archive}（默认 {@code /archive/{year}/{month}/}）。 */
    private String archiveUrl(int year, Integer month) {
        String pattern = option("url.archive", "/archive/{year}/{month}/");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("year", year);
        if (month != null) {
            values.put("month", month);
        }
        values.put("lang", site().lang());
        return resolveUrl(pattern, values,
                month == null ? "/archive/" + year + "/" : "/archive/" + year + "/" + month + "/");
    }

    /** 筛选页 URL：站点选项 {@code url.facet}（默认 {@code /f/{facetPath}/}，§7.4）。 */
    private String facetUrl(String facetPath) {
        String pattern = option("url.facet", "/f/{facetPath}/");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("facetPath", facetPath);
        values.put("lang", site().lang());
        return resolveUrl(pattern, values, "/f/" + facetPath + "/");
    }

    /** 类型列表页的第 1 页形态；{@code list_url_pattern} 为空时退回 {@code /{typeCode}/}（§6.4）。 */
    private String typeListUrl(ContentTypeDef def) {
        String pattern = def.listUrlPattern();
        if (pattern == null || pattern.isBlank()) {
            return "/" + def.code() + "/";
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("typeCode", def.code());
        values.put("lang", site().lang());
        return resolveUrl(pattern, values, "/" + def.code() + "/");
    }

    /**
     * 调用共享的 {@link UrlPatternResolver}；模式串脏（白名单外的占位符）时退回兜底形态而**不抛异常**
     * ——发布任务不该因为某一条内容的 URL 规则打挂整批（§7.1.1 的校验在编译期 / 计划期报 E4001）。
     */
    private String resolveUrl(String pattern, Map<String, Object> values, String fallback) {
        try {
            String url = UrlPatternResolver.resolve(pattern, values);
            return url == null || url.isBlank() ? fallback : url;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /* ================= 分类 / 标签的读取与缓存 ================= */

    private List<CmsCategory> categoryRows() {
        if (categoryRows == null) {
            categoryRows = mappers.categoryMapper().selectList(new LambdaQueryWrapper<CmsCategory>()
                    .eq(CmsCategory::getSiteId, siteId)
                    .orderByAsc(CmsCategory::getSort)
                    .orderByAsc(CmsCategory::getId));
            categoryById = null;
            categoryBySlug = null;
        }
        return categoryRows;
    }

    private Map<Long, CmsCategory> categoryById() {
        if (categoryById == null) {
            Map<Long, CmsCategory> map = new LinkedHashMap<>();
            for (CmsCategory row : categoryRows()) {
                map.put(row.getId(), row);
            }
            categoryById = map;
        }
        return categoryById;
    }

    private Map<String, CmsCategory> categoryBySlug() {
        if (categoryBySlug == null) {
            Map<String, CmsCategory> map = new LinkedHashMap<>();
            for (CmsCategory row : categoryRows()) {
                if (row.getSlug() != null) {
                    map.putIfAbsent(row.getSlug(), row);
                }
            }
            categoryBySlug = map;
        }
        return categoryBySlug;
    }

    /** 一层子分类（按 {@code sort, id}）。 */
    private List<CmsCategory> categoriesOf(long parentId) {
        List<CmsCategory> level = new ArrayList<>();
        for (CmsCategory row : categoryRows()) {
            long parent = row.getParentId() == null ? 0L : row.getParentId();
            if (parent == parentId) {
                level.add(row);
            }
        }
        return level;
    }

    /** 分类 id 或 slug（slug 优先，数字再当 id）；解析不出来返回 null。 */
    private CmsCategory resolveCategory(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank() || "all".equals(idOrSlug)) {
            return null;
        }
        CmsCategory bySlug = categoryBySlug().get(idOrSlug);
        if (bySlug != null) {
            return bySlug;
        }
        Long id = asLong(idOrSlug);
        return id == null ? null : categoryById().get(id);
    }

    /** 分类的完整 slug 路径（{@code /news/tech}，含自身）。 */
    private String categoryPath(long categoryId) {
        StringBuilder path = new StringBuilder();
        Set<Long> seen = new HashSet<>();
        Long cursor = categoryId;
        while (cursor != null && cursor > 0 && seen.add(cursor)) {
            CmsCategory node = categoryById().get(cursor);
            if (node == null) {
                break;
            }
            path.insert(0, "/" + (node.getSlug() == null ? node.getId() : node.getSlug()));
            cursor = node.getParentId();
        }
        return path.length() == 0 ? "/" : path.toString();
    }

    /** 一个内容的**主分类**（{@code dimension='primary'}，§2.3）：它的 path 决定 URL 与面包屑。 */
    private CmsCategory primaryCategory(long contentId) {
        for (Rows.RelationRow relation : mappers.contentCategoryMapper()
                .selectContentCategories(List.of(contentId))) {
            if ("primary".equals(relation.getDimension())) {
                CmsCategory node = categoryById().get(relation.getRefId());
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private List<CmsTag> tagRows() {
        if (tagRows == null) {
            tagRows = mappers.tagMapper().selectList(new LambdaQueryWrapper<CmsTag>()
                    .eq(CmsTag::getSiteId, siteId)
                    .orderByAsc(CmsTag::getName)
                    .orderByAsc(CmsTag::getId));
            tagById = null;
            tagBySlug = null;
        }
        return tagRows;
    }

    private Map<Long, CmsTag> tagById() {
        if (tagById == null) {
            Map<Long, CmsTag> map = new LinkedHashMap<>();
            for (CmsTag row : tagRows()) {
                map.put(row.getId(), row);
            }
            tagById = map;
        }
        return tagById;
    }

    private Map<String, CmsTag> tagBySlug() {
        if (tagBySlug == null) {
            Map<String, CmsTag> map = new LinkedHashMap<>();
            for (CmsTag row : tagRows()) {
                if (row.getSlug() != null) {
                    map.putIfAbsent(row.getSlug(), row);
                }
            }
            tagBySlug = map;
        }
        return tagBySlug;
    }

    private CmsTag resolveTag(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            return null;
        }
        CmsTag bySlug = tagBySlug().get(idOrSlug);
        if (bySlug != null) {
            return bySlug;
        }
        Long id = asLong(idOrSlug);
        return id == null ? null : tagById().get(id);
    }

    private Long resolveTagId(String idOrSlug) {
        CmsTag tag = resolveTag(idOrSlug);
        return tag == null ? null : tag.getId();
    }

    /** 一个分类下已发布内容的条数；{@code CountScope.tree} 含后代分类（§6.4）。 */
    private long categoryCount(long categoryId, CountScope scope) {
        if (scope != CountScope.tree) {
            return categoryNodeCounts().getOrDefault(categoryId, 0L);
        }
        long count = 0L;
        for (Long id : subtreeCategoryIds(categoryId)) {
            count += categoryNodeCounts().getOrDefault(id, 0L);
        }
        return count;
    }

    private List<Long> subtreeCategoryIds(long categoryId) {
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        // 只用 seen 防环，不再设硬上限：4096 之类的截断会让超大分类树下的 tree 计数
        // （以及 includeChildren=true 的列表/归档）静默少算，且没有任何提示。
        while (!queue.isEmpty()) {
            long current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            ids.add(current);
            for (CmsCategory child : categoriesOf(current)) {
                queue.add(child.getId());
            }
        }
        return ids;
    }

    /** 每个分类的"本节点"内容数，一次 SQL 取全部（导航计数与 {@code category().count} 共用）。 */
    private Map<Long, Long> categoryNodeCounts() {
        if (categoryNodeCounts == null) {
            Map<Long, Long> counts = new HashMap<>();
            for (Rows.CategoryCountRow row : mappers.contentCategoryMapper()
                    .selectCategoryCounts(baseParams())) {
                counts.put(row.getCategoryId(), row.getCount() == null ? 0L : row.getCount());
            }
            categoryNodeCounts = counts;
        }
        return categoryNodeCounts;
    }

    private long tagContentCount(long tagId) {
        if (tagContentCounts == null) {
            Map<Long, Long> counts = new HashMap<>();
            for (Rows.TagCountRow row : mappers.contentTagMapper()
                    .selectTagCounts(baseParams(), "sort", 0, 0)) {
                counts.put(row.getId(), row.getCount() == null ? 0L : row.getCount());
            }
            tagContentCounts = counts;
        }
        return tagContentCounts.getOrDefault(tagId, 0L);
    }

    /* ================= 一页内容的批量数据 ================= */

    /**
     * 一个内容页的批量加载器：分类、标签、父内容、子内容数、被引用内容、作者、媒体各一次 SQL，
     * 然后在内存里分发。**"列表查询不做 N+1"的落点就是它**。
     */
    private final class Batch {

        private final Map<Long, List<Long>> categoryIds = new HashMap<>();
        private final Map<Long, Map<Long, String>> dimensions = new HashMap<>();
        private final Map<Long, List<Long>> tagIds = new HashMap<>();
        private final Map<Long, ContentRow> parents = new HashMap<>();
        private final Map<Long, Long> childCounts = new HashMap<>();
        private final Map<Long, ContentRow> referenced = new HashMap<>();
        private final Map<Long, CmsMedia> media = new HashMap<>();
        private final Set<Long> ids = new LinkedHashSet<>();
        private final List<ContentRow> rows;

        private Batch(List<ContentRow> rows) {
            this.rows = rows;
            for (ContentRow row : rows) {
                ids.add(row.idOrZero());
            }
        }

        private void load(String typeCode) {
            List<Long> contentIds = new ArrayList<>(ids);
            for (Rows.RelationRow relation : mappers.contentCategoryMapper()
                    .selectContentCategories(contentIds)) {
                categoryIds.computeIfAbsent(relation.getContentId(), key -> new ArrayList<>())
                        .add(relation.getRefId());
                dimensions.computeIfAbsent(relation.getContentId(), key -> new HashMap<>())
                        .put(relation.getRefId(), relation.getDimension());
            }
            for (Rows.RelationRow relation : mappers.contentTagMapper()
                    .selectContentTags(contentIds)) {
                tagIds.computeIfAbsent(relation.getContentId(), key -> new ArrayList<>())
                        .add(relation.getRefId());
            }
            Set<Long> parentIds = new LinkedHashSet<>();
            Set<Long> relatedIds = new LinkedHashSet<>();
            Set<Long> mediaIds = new LinkedHashSet<>();
            for (ContentRow row : rows) {
                if (row.parentIdOrZero() > 0) {
                    parentIds.add(row.parentIdOrZero());
                }
                if (row.getAuthorId() != null) {
                    relatedIds.add(row.getAuthorId());
                }
                collectReferences(row, relatedIds, mediaIds);
            }
            for (ContentRow row : findRows(parentIds)) {
                parents.put(row.idOrZero(), row);
            }
            for (ContentRow row : findRows(relatedIds)) {
                referenced.put(row.idOrZero(), row);
            }
            ProviderParams childParams = baseParams();
            boolean cross = typeCode == null || "all".equals(typeCode);
            childParams.setTypeAll(cross);
            childParams.setTypeCode(cross ? null : typeCode);
            childParams.setParentIds(contentIds);
            for (Rows.ChildCountRow row : mappers.contentMapper().selectChildCounts(childParams)) {
                childCounts.put(row.getParentId(), row.getCount() == null ? 0L : row.getCount());
            }
            loadMedia(mediaIds);
        }

        /** 收集一页内容里 {@code RELATION} 引用的内容 id 与 {@code IMAGE} / {@code FILE} 引用的媒体 id。 */
        private void collectReferences(ContentRow row, Set<Long> relatedIds, Set<Long> mediaIds) {
            ContentTypeDef def = type(row.getTypeCode());
            if (def == null) {
                return;
            }
            Map<String, Object> data = Json.object(row.getData());
            for (FieldDef field : def.fields()) {
                Object raw = data.get(field.code());
                if (raw == null) {
                    continue;
                }
                switch (field.fieldType()) {
                    case RELATION -> {
                        for (String token : storedValues(raw)) {
                            Long id = asLong(token);
                            if (id != null) {
                                relatedIds.add(id);
                            }
                        }
                    }
                    case IMAGE, FILE -> {
                        Long id = asLong(text(raw));
                        if (id != null) {
                            mediaIds.add(id);
                        }
                    }
                    case IMAGES, FILES -> {
                        for (Object element : listOf(raw)) {
                            Long id = asLong(text(element));
                            if (id != null) {
                                mediaIds.add(id);
                            }
                        }
                    }
                    default -> {
                        // 其余字段类型不需要额外查询
                    }
                }
            }
        }

        private void loadMedia(Set<Long> mediaIds) {
            if (mediaIds.isEmpty()) {
                return;
            }
            List<CmsMedia> found = mappers.mediaMapper().selectList(new LambdaQueryWrapper<CmsMedia>()
                    .eq(CmsMedia::getSiteId, siteId)
                    .in(CmsMedia::getId, mediaIds));
            for (CmsMedia row : found) {
                media.put(row.getId(), row);
            }
        }

        /** 一条内容的分类节点，主分类排第一（§2.3）。 */
        private List<CmsCategory> categoriesOf(long contentId) {
            List<Long> refs = categoryIds.get(contentId);
            if (refs == null || refs.isEmpty()) {
                return List.of();
            }
            List<CmsCategory> nodes = new ArrayList<>();
            for (Long ref : refs) {
                CmsCategory node = categoryById().get(ref);
                if (node != null) {
                    nodes.add(node);
                }
            }
            nodes.sort((left, right) -> {
                boolean leftPrimary = "primary".equals(dimensionOf(contentId, left.getId()));
                boolean rightPrimary = "primary".equals(dimensionOf(contentId, right.getId()));
                if (leftPrimary != rightPrimary) {
                    return leftPrimary ? -1 : 1;
                }
                return Long.compare(left.getId(), right.getId());
            });
            return nodes;
        }

        private CmsCategory primaryCategory(long contentId) {
            for (CmsCategory node : categoriesOf(contentId)) {
                if ("primary".equals(dimensionOf(contentId, node.getId()))) {
                    return node;
                }
            }
            return null;
        }

        private String dimensionOf(long contentId, long categoryId) {
            return dimensions.getOrDefault(contentId, Map.of()).get(categoryId);
        }

        private List<Long> tagIdsOf(long contentId) {
            List<Long> refs = tagIds.get(contentId);
            return refs == null ? List.of() : List.copyOf(refs);
        }

        private List<CmsTag> tagsOf(long contentId) {
            List<CmsTag> nodes = new ArrayList<>();
            for (Long ref : tagIdsOf(contentId)) {
                CmsTag tag = tagById().get(ref);
                if (tag != null) {
                    nodes.add(tag);
                }
            }
            return nodes;
        }

        /** 祖先链（从顶到下）：父内容不在本页里时补查，并放进本批的缓存（兄弟内容共享）。 */
        private List<ContentRow> ancestors(ContentRow row) {
            List<ContentRow> chain = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            Long cursor = row.parentIdOrZero();
            int guard = 0;
            while (cursor != null && cursor > 0 && seen.add(cursor) && guard++ < MAX_HIERARCHY) {
                ContentRow parent = parents.get(cursor);
                if (parent == null) {
                    parent = findRow(cursor);
                    if (parent != null) {
                        parents.put(parent.idOrZero(), parent);
                    }
                }
                if (parent == null) {
                    break;
                }
                chain.add(parent);
                cursor = parent.parentIdOrZero();
            }
            java.util.Collections.reverse(chain);
            return chain;
        }
    }

    /* ================= 小工具 ================= */

    private static boolean flag(Integer value) {
        return value != null && value != 0;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 一个自定义字段值的多值形态（数组 / JSON 串 / 逗号串 / 单值都归一成字符串列表）。 */
    static List<String> storedValues(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) {
                    values.add(String.valueOf(element).trim());
                }
            }
            return values;
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(raw, i);
                if (element != null) {
                    values.add(String.valueOf(element).trim());
                }
            }
            return values;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return values;
        }
        if (value.startsWith("[") || value.startsWith("{")) {
            List<String> parsed = Json.asTextList(Json.parse(value));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        for (String piece : value.split(",")) {
            if (!piece.isBlank()) {
                values.add(piece.trim());
            }
        }
        return values;
    }

    private static List<Object> listOf(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (raw instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        return List.of(raw);
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Json.asObject(map);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalize(element));
            }
            return normalized;
        }
        return value;
    }

    private static Object number(Object raw) {
        if (raw instanceof Number) {
            return raw;
        }
        String value = text(raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return value.contains(".") ? new BigDecimal(value) : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static Boolean bool(Object raw) {
        if (raw instanceof Boolean flag) {
            return flag;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = text(raw).trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static boolean isNumeric(String text) {
        return text.matches("[-+]?\\d+");
    }

    /** 时间字符串 → {@code LocalDateTime}；认不出返回 null（调用方保留原值）。 */
    static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm")) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (RuntimeException ignored) {
                // 换下一种形态
            }
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            // 继续试日期形态
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 纯文本正文字数：去掉标签与空白后的字符数（§2.2 的 {@code wordCount} 兜底口径）。 */
    static int plainLength(String html) {
        String text = html.replaceAll("<[^>]*>", "");
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String trimSlashes(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String lastSegment(String path) {
        String trimmed = trimSlashes(path);
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    static Long asLong(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
