package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.Anchor;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.WhereParser;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §6.3 的"共用参数表"到 {@link ContentQuery} 的解析（{@code {cms:list}} / {@code {cms:query}} /
 * {@code {cms:detail}} 三个标签共用）。
 *
 * <p>它做四件事，每件都只做一次：
 * <ol>
 *   <li><b>缺省值</b>：{@code type} / {@code category} 缺省 = 当前页面的值，{@code row} 缺省由标签给
 *       （{@code list} 取类型 {@code per_page}、{@code query} 取 10、{@code detail} 取 1），
 *       {@code orderby} 缺省 = 类型的 {@code sort_field} / {@code sort_order}；</li>
 *   <li><b>类型转换</b>：{@code includeChildren} / {@code top} / {@code recommend} 按
 *       {@link #bool} 解析，{@code row} / {@code offset} / {@code depth} 按 {@link #intValue}；</li>
 *   <li><b>复用既有解析器</b>：{@code where} 走 {@link WhereParser}，{@code orderby} 走
 *       {@link OrderBy#parseList}——转义规则与"末尾追加 id desc"的口径因此只有一份实现；</li>
 *   <li><b>必填判定</b>：{@code type} 与 {@code category} 的"当前值不存在时必填"（§6.3 那两行括号）。
 *       编译期由 §4.5 第 7 条报 E1002；这里是同一条规则的**渲染期兜底**，报同样的码。</li>
 * </ol>
 *
 * <p><b>三处缺省按页面类型分叉</b>，逐条对着契约：
 * <ul>
 *   <li>{@code {cms:list}} 在**分类索引页（{@code LIST}）**上缺省 {@code type='all'} 与
 *       {@code category=当前分类}（§7.2.1："该分类下所有类型的混合流——这正是『栏目』的语义"）；</li>
 *   <li>其余没有内容类型的页面（{@code HOME} / {@code TAGPAGE} / {@code ARCHIVE} / {@code FACET}）
 *       省略 {@code type} → E1002（§6.3 的清单**不含 LIST**）；</li>
 *   <li>{@code {cms:query}} 不在 §7.2.1 的缺省规则里，因此它一律按 §6.3 的清单走。</li>
 * </ul>
 *
 * <p><b>{@code type='all'} 与 {@code where} / {@code relate='field:<code>'} / {@code of} 互斥</b>
 * （§6.3 v2.2 定死 → E2007）：跨类型取数只按 {@code cms_content} 公共列，自定义字段与层级都不成立。
 */
public final class QueryParams {

    private QueryParams() {
    }

    /**
     * 解析一个查询标签的参数。
     *
     * @param node       标签节点（参数值与报错位置都从这里取）
     * @param ctx        渲染上下文（{@code type} / {@code category} 的缺省值来自"当前页面"）
     * @param provider   取数出口，用来查类型定义与锚定项坐标
     * @param rowDefault {@code row} 参数缺省值；{@code <=0} 表示标签没给缺省（退回 0 = 不分页）
     * @param anchor     锚定项坐标（{@code of} / {@code relate} 用）；没有锚定项时传 {@link Anchor#NONE}
     */
    public static ContentQuery resolve(TagNode node, RenderContext ctx, ContentProvider provider,
                                       int rowDefault, Anchor anchor) {
        return resolve(node, ctx, provider, rowDefault, anchor, false, true, List.of());
    }

    /**
     * 完整形态。
     *
     * @param listPageDefaults           {@code true} 时启用 §7.2.1 的"分类索引页缺省"：
     *                                   {@code type} 缺省 {@code all}、{@code category} 缺省当前栏目
     * @param unlimitedCategoryIfMissing {@code true} 时把缺失的当前栏目当成 {@code category='all'} 而不报错
     *                                   （{@code {cms:detail}} 取一条内容，本来就不筛栏目）
     * @param accepted                   只接受这些参数；空表 = 全部接受。
     *                                   {@code {cms:detail}} 的参数表只有 {@code type}（§6.3），
     *                                   多写的参数必须报出来而不是静默忽略（§1.3）
     */
    public static ContentQuery resolve(TagNode node, RenderContext ctx, ContentProvider provider,
                                       int rowDefault, Anchor anchor, boolean listPageDefaults,
                                       boolean unlimitedCategoryIfMissing, List<String> accepted) {
        ContentQuery query = new ContentQuery();

        String type = accepted(node, accepted, "type");
        if (type == null || type.isBlank()) {
            type = currentType(ctx);
            // §7.2.1：分类索引页的 {cms:list} 缺省 type='all'。
            // 用 sourcePageType() 而不是 pageType()：分类索引页的第 2..N 页是 DPAGE，
            // 但它的来源是 LIST——缺省必须照样生效，否则"分类页 + 分页"无解（§5.4：同一模板、同一上下文）。
            if (type == null && listPageDefaults && inheritsListPage(ctx)) {
                type = "all";
            }
            if (type == null) {
                throw E1002(node, "type", "当前页面（" + ctx.pageType() + "）没有内容类型",
                        "显式写一个类型 code，或不限类型写 type='all'");
            }
        }
        query.type(type);
        checkTypeExists(node, provider, type);

        String category = accepted(node, accepted, "category");
        if (category == null || category.isBlank()) {
            category = currentCategory(ctx);
            if (category == null && !unlimitedCategoryIfMissing) {
                throw E1002(node, "category", "当前页面（" + ctx.pageType() + "）没有当前栏目",
                        "不限栏目请写 category='all'，或给一个具体分类的 id / slug");
            }
            if (category == null) {
                category = "all";
            }
        }
        query.category(category);
        checkCategoryExists(node, provider, category);

        String includeChildren = accepted(node, accepted, "includeChildren");
        if (includeChildren != null) {
            query.includeChildren(bool(node, "includeChildren"));
        }
        query.tags(splitTags(accepted(node, accepted, "tag")));
        query.author(resolveAuthor(node, ctx, accepted(node, accepted, "author")));
        query.of(enumValue(node, accepted(node, accepted, "of"), "of", "self", "parent"));
        query.relate(relateValue(node, accepted(node, accepted, "relate")));
        query.excludeSelf("self".equals(accepted(node, accepted, "exclude")));
        query.where(WhereParser.parse(accepted(node, accepted, "where"), node.sourcePath(), node.lineNo()));
        query.keyword(accepted(node, accepted, "keyword"));
        // 与 includeChildren / where 同一口径：标签没声明这个参数时按"没写"处理（§1.3）
        String top = accepted(node, accepted, "top");
        query.top(top == null ? null : bool(node, "top"));
        String recommend = accepted(node, accepted, "recommend");
        query.recommend(recommend == null ? null : bool(node, "recommend"));
        String status = accepted(node, accepted, "status");
        query.status(status == null || status.isBlank() ? "PUBLISHED" : status);

        query.orderby(resolveOrderby(node, provider, query, accepted));
        query.row(accepted(node, accepted, "row") == null
                ? Math.max(rowDefault, 0) : intValue(node, "row"));
        if (accepted(node, accepted, "offset") != null) {
            query.offset(intValue(node, "offset"));
        }
        if (accepted(node, accepted, "depth") != null) {
            query.depth(intValue(node, "depth"));
        }
        query.anchor(anchor.id(), anchor.parentId());

        if (query.crossType()) {
            checkCrossTypeRestrictions(node, query);
        }
        return query;
    }

    /**
     * {@code orderby} 的三条口径（§6.3）：
     * <ol>
     *   <li>{@code relationOrder} **只有裸值才算**（"只在 {@code relate='field:<code>'} 时可用"）；
     *       带方向或多字段的写法 → E2007；</li>
     *   <li>未写 {@code orderby} → 类型的 {@code sort_field} / {@code sort_order}，
     *       未配时 {@code publishTime desc}；</li>
     *   <li>写了字段但没写方向（{@code orderby='sort'}）→ 方向取类型的 {@code sort_order}
     *       （{@code order} 参数是单字段简写，能覆盖它）。</li>
     * </ol>
     */
    private static List<OrderBy> resolveOrderby(TagNode node, ContentProvider provider, ContentQuery query,
                                                List<String> accepted) {
        String raw = accepted(node, accepted, "orderby");
        String order = accepted(node, accepted, "order");
        ContentTypeDef def = provider.type(query.typeCode());

        if (raw != null && BuiltinFields.RELATION_ORDER.equals(raw.trim())) {
            checkRelationOrderUsable(node, accepted);
            // 数组顺序由取数层按这个标志排（§6.3）：排序项为空，次序完全来自 RELATION 数组
            query.relationOrder(true);
            return List.of();
        }
        if (raw != null && raw.trim().startsWith(BuiltinFields.RELATION_ORDER)) {
            throw PublishException.error(PublishErrorCode.E2007,
                    "orderby='relationOrder' 只能裸写", node.sourcePath(), node.lineNo(),
                    "模板里写的是 orderby='" + raw + "'",
                    "数组顺序没有方向与多字段：写 orderby='relationOrder'（不带 desc / asc，不与其他字段并列）");
        }
        if (raw == null || raw.isBlank()) {
            List<OrderBy> defaults = defaultOrder(def);
            return order == null ? defaults : applyDirection(defaults, order);
        }
        List<OrderBy> parsed = OrderBy.parseList(raw);
        if (order != null) {
            return applyDirection(parsed, order);
        }
        // 单字段且模板没写方向 → 方向取类型的 sort_order；类型未配（或没有类型）时与
        // defaultOrder 同口径取 desc（§6.3："未配时 publishTime desc"）
        if (parsed.size() == 1 && !hasDirection(raw)) {
            boolean desc = def == null || def.sortOrder() == null
                    || !"asc".equalsIgnoreCase(def.sortOrder());
            return List.of(new OrderBy(parsed.get(0).fieldCode(), desc));
        }
        return parsed;
    }

    /** {@code relationOrder} 只对 {@code relate='field:<code>'} 成立（§6.3）。 */
    private static void checkRelationOrderUsable(TagNode node, List<String> accepted) {
        String relate = accepted(node, accepted, "relate");
        if (relate != null && relate.startsWith("field:")) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E2007,
                "orderby='relationOrder' 需要 relate='field:<code>'", node.sourcePath(), node.lineNo(),
                "relate=" + (relate == null ? "（没写）" : "'" + relate + "'"),
                "数组顺序来自某个 RELATION 字段：写 relate='field:<code>' 与 orderby='relationOrder'");
    }

    private static boolean hasDirection(String raw) {
        for (String piece : raw.split(",")) {
            String[] parts = piece.trim().split("\\s+");
            if (parts.length > 1) {
                return true;
            }
        }
        return false;
    }

    /** 单字段排序简写 {@code order}：只改方向，字段沿用 {@code orderby}（或类型默认）。 */
    private static List<OrderBy> applyDirection(List<OrderBy> orderby, String order) {
        String text = order == null ? "" : order.trim();
        if (!"asc".equalsIgnoreCase(text) && !"desc".equalsIgnoreCase(text)) {
            throw PublishException.error(PublishErrorCode.E1002, "参数 order 的取值不可用",
                    null, 0, "order='" + order + "'，期望 asc / desc",
                    "参数表见 §6.3；只接受 asc 与 desc");
        }
        boolean desc = "desc".equalsIgnoreCase(text);
        List<OrderBy> result = new ArrayList<>(orderby.size());
        for (OrderBy item : orderby) {
            result.add(new OrderBy(item.fieldCode(), desc));
        }
        return result;
    }

    /** 取一个参数，但只在这个标签声明过它时；没声明 = 模板写了也不认（调用方按"没写"处理）。 */
    private static String accepted(TagNode node, List<String> accepted, String key) {
        if (!accepted.isEmpty() && !accepted.contains(key)) {
            return null;
        }
        return node.arg(key);
    }

    /**
     * 当前页面的内容类型 code；首页 / 标签页 / 归档页 / 筛选页上没有（§6.3）。
     * 分类索引页（{@code LIST}）上它是 null，但 {@code {cms:list}} 的 {@code type} 缺省是
     * {@code 'all'}（§7.2.1），由调用方给。
     */
    public static String currentType(RenderContext ctx) {
        String type = ctx.typeCode();
        return type == null || type.isBlank() ? null : type;
    }

    /**
     * 当前页面的"当前栏目"：{@code channel} 具名作用域里的 {@code id}（§5.1 第（4）条那张表）。
     * 首页 / 标签页 / 归档页 / 筛选页上没有它 → 返回 null，调用方按页面类型决定报错还是取 {@code all}。
     *
     * <p>与 {@code CurrentMarks.currentCategoryId} 同一口径：只有**分类来源**的列表页 / 详情页
     * （含派生页）的 {@code channel} 才是分类。类型列表页的 {@code channel} 是内容类型
     * （{@code SitePlanner.typeChannel}，带 {@code typeCode}）、标签页是标签、单页是内容自身——
     * 它们的 {@code id} 与分类 id 不同源，当成分类用会筛出错误结果或报出与事实不符的 E2006。
     */
    public static String currentCategory(RenderContext ctx) {
        PageType pageType = ctx.pageType();
        if (pageType != PageType.LIST && pageType != PageType.DETAIL && pageType != PageType.DPAGE) {
            return null;
        }
        Map<String, Object> channel = ctx.namedValues("channel");
        if (channel.isEmpty() || channel.containsKey("typeCode")) {
            return null;
        }
        Object id = channel.get("id");
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 这一页是不是（或继承自）**分类索引页**——即 §7.2.1 那两条缺省该不该生效。
     *
     * <p>为什么不能只看 {@code pageType()}：分类索引页的第 2..N 页是 {@code DPAGE}（它决定
     * noindex 与 sitemap），而它的来源是 {@code LIST}。按 {@code pageType()} 判会让缺省在第二页起
     * 失效——那等于"分类页不能分页"，而这正是 §5.4 派生页模型要支持的头号场景。
     *
     * <p>排除类型列表页：那一种的 {@code channel} 是**内容类型**、不是分类
     * （{@code CurrentMarks.currentCategoryId} 也是这么区分的），所以"缺省 category = 当前栏目"
     * 在它身上不成立；它的 {@code type} 缺省来自页面自身的类型，本来就不需要兜底。
     */
    private static boolean inheritsListPage(RenderContext ctx) {
        if (ctx.sourcePageType().matrixColumn() != com.lingjiuw.cms.module.cms.publish.model.PageType.LIST) {
            return false;
        }
        return !ctx.namedValues("channel").containsKey("typeCode");
    }

    /** 只读出"这次查询是哪个类型"，供 {@code row} 缺省值用（{@code list} 要看类型的 {@code per_page}）。 */
    public static String typeCodeOf(TagNode node, RenderContext ctx) {
        String type = node.arg("type");
        return type == null || type.isBlank() ? currentType(ctx) : type;
    }

    private static void checkTypeExists(TagNode node, ContentProvider provider, String type) {
        if ("all".equals(type) || provider.type(type) != null) {
            return;
        }
        List<String> codes = new ArrayList<>();
        for (ContentTypeDef def : provider.types()) {
            codes.add(def.code());
        }
        throw PublishException.error(PublishErrorCode.E2006,
                "内容类型 " + type + " 不存在", node.sourcePath(), node.lineNo(),
                "本站没有类型 " + type,
                "本站类型有 " + String.join(" ", codes) + "；不限类型请写 type='all'");
    }

    private static void checkCategoryExists(TagNode node, ContentProvider provider, String category) {
        if ("all".equals(category) || provider.categoryExists(category)) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E2006,
                "分类 " + category + " 不存在", node.sourcePath(), node.lineNo(),
                "本站没有 id 或 slug 为 " + category + " 的分类",
                "不限栏目请写 category='all'");
    }

    /**
     * {@code type='all'} 的互斥项（§6.3 v2.2 定死）：
     * {@code where} / {@code relate='field:<code>'} / {@code of} 配 {@code type='all'} → E2007。
     */
    private static void checkCrossTypeRestrictions(TagNode node, ContentQuery query) {
        String offending = null;
        if (!query.where().isEmpty()) {
            offending = "where";
        } else if (query.relate() != null && query.relate().startsWith("field:")) {
            offending = "relate='" + query.relate() + "'";
        } else if (query.of() != null) {
            offending = "of='" + query.of() + "'";
        }
        if (offending == null) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E2007,
                "type='all' 不能与 " + offending + " 同时使用", node.sourcePath(), node.lineNo(),
                "模板里同时写了 type='all' 与 " + offending,
                "跨类型取数只按 cms_content 公共列，自定义字段与层级都不成立；"
                        + "请去掉 " + offending + "，或把 type 换成具体类型"
                        + "（§6.3：type='all' 时迭代项可用字段写死为公共列 + 不依赖类型的派生字段）");
    }

    /** {@code author='self'} = 当前条目的作者（§6.3 v2.1 新增）。 */
    private static String resolveAuthor(TagNode node, RenderContext ctx, String author) {
        if (author == null || !"self".equals(author.trim())) {
            return author;
        }
        if (!ctx.pageType().hasCurrentEntry()) {
            throw PublishException.error(PublishErrorCode.E2009,
                    "{cms:" + node.name() + "} 的 author='self' 无法解析", node.sourcePath(), node.lineNo(),
                    "当前页面类型 " + ctx.pageType() + " 没有当前条目，取不到作者",
                    "把 author 换成一个具体的作者 id / slug");
        }
        ContentItem item = ctx.currentItem();
        Object value = item == null ? null : item.get("authorId");
        if (value == null) {
            throw PublishException.error(PublishErrorCode.E2009,
                    "{cms:" + node.name() + "} 的 author='self' 无法解析", node.sourcePath(), node.lineNo(),
                    // currentItem() 可能仍为 null（页面允许传 null）：文案里不能再无条件解引用
                    "当前条目" + (item == null ? "（没有取到）" : " " + item.title()) + " 没有 authorId",
                    "给这条内容指定作者，或把 author 换成一个具体的作者 id / slug");
        }
        return String.valueOf(value);
    }

    /** {@code relate} 是"枚举 + 前缀形式"，前缀形式只能运行期判（§6.3）。 */
    private static String relateValue(TagNode node, String relate) {
        if (relate == null || relate.isBlank()) {
            return null;
        }
        String text = relate.trim();
        if ("tag".equals(text) || "category".equals(text)) {
            return text;
        }
        if (text.startsWith("field:") && text.length() > "field:".length()) {
            return text;
        }
        throw PublishException.error(PublishErrorCode.E2007,
                "参数 relate 的取值不可用", node.sourcePath(), node.lineNo(),
                "relate='" + relate + "'，期望 tag / category / field:<code>",
                "参数表见 §6.3；field:<code> 里的 code 必须是 indexed=1 的自定义字段或内置字段");
    }

    /** ENUM 参数的取值校验（参数声明是 ENUM，这里是渲染期兜底）。 */
    private static String enumValue(TagNode node, String value, String key, String... allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String candidate : allowed) {
            if (candidate.equals(value.trim())) {
                return candidate;
            }
        }
        throw PublishException.error(PublishErrorCode.E1002,
                "参数 " + key + " 的取值不可用", node.sourcePath(), node.lineNo(),
                "参数 " + key + " = '" + value + "'，期望 " + String.join(" / ", allowed),
                "参数表见 §6.3");
    }

    /** {@code tag} 的多值分隔符是 {@code |}，多值之间 AND（§6.3）。 */
    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String piece : raw.split("\\|")) {
            String text = piece.trim();
            if (!text.isEmpty()) {
                tags.add(text);
            }
        }
        return tags;
    }

    /** 类型的默认排序；未配时 {@code publishTime desc}（§6.3）。 */
    public static List<OrderBy> defaultOrder(ContentTypeDef def) {
        if (def == null) {
            return List.of(new OrderBy("publishTime", true));
        }
        boolean desc = def.sortOrder() == null || !"asc".equalsIgnoreCase(def.sortOrder());
        return List.of(new OrderBy(def.sortFieldOrDefault(), desc));
    }

    /** §3.3 的 BOOL：只接受 {@code 0} {@code 1} {@code true} {@code false}（忽略大小写）。 */
    public static boolean bool(TagNode node, String key) {
        Boolean value = boolOrNull(node, key);
        if (value == null) {
            throw E1002(node, key, "解析不出布尔值", "只接受 0 / 1 / true / false");
        }
        return value;
    }

    /** 同上，但参数缺失时返回 null（用于"模板没写"与"写了 0"必须区分的地方）。 */
    public static Boolean boolOrNull(TagNode node, String key) {
        String raw = node.arg(key);
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw E1002(node, key, "参数值 '" + raw + "' 解析不出布尔值", "只接受 0 / 1 / true / false");
    }

    /** §3.3 的 INT：解析不出非负整数时给 E1002，并贴出实际值。 */
    public static int intValue(TagNode node, String key) {
        String raw = node.arg(key);
        if (raw == null || raw.isBlank()) {
            throw E1002(node, key, "参数值 '" + raw + "' 不是整数", "写一个非负整数");
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw E1002(node, key, "参数值 '" + raw + "' 是负数", "写一个非负整数");
            }
            return value;
        } catch (NumberFormatException e) {
            throw E1002(node, key, "参数值 '" + raw + "' 解析不出整数", "写一个非负整数");
        }
    }

    private static PublishException E1002(TagNode node, String key, String actual, String advice) {
        return PublishException.error(PublishErrorCode.E1002,
                "参数 " + key + " 不可用", node.sourcePath(), node.lineNo(), actual, advice);
    }
}
