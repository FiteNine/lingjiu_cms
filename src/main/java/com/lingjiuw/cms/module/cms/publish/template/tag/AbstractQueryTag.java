package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.template.Pagination;
import com.lingjiuw.cms.module.cms.publish.template.PaginationKind;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.QueryParams;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三个查询标签（{@code {cms:list}} / {@code {cms:query}} / {@code {cms:detail}}）共用的骨架。
 *
 * <p>三者共用 §6.3 的"共用参数表"、共用"迭代项要补 {@code current} / {@code class}"这条口径、
 * 共用"压栈 → 渲染标签体 → 弹栈"的循环写法。不同之处各由子类给：
 * <ul>
 *   <li>{@link #rowDefault} —— {@code row} 缺省值（{@code list} 取类型 {@code per_page}、
 *       {@code query} 取 10、{@code detail} 取 1）；</li>
 *   <li>{@link #paginates} —— 是不是分页主体（{@code list} 恒是；{@code query} 不是；
 *       {@code detail} 只在类型的 {@code paginate_body} 非空时才是，§4.5 口径表）；</li>
 *   <li>{@link #acceptedParams} —— 这个标签**声明**了哪些参数。没声明的参数一律按"没写"处理，
 *       绝不静默生效（§1.3："不认识的参数静默忽略"是明确禁止的）；</li>
 *   <li>{@link #renderBody} / {@link #renderQuery} —— 两种渲染方式；</li>
 *   <li>{@link #maxCount} —— §6.7 矩阵给的行。</li>
 * </ul>
 *
 * <p><b>无状态</b>：全部中间结果放 {@link RenderContext#putNodeState}，因此同一个 AST 可以被
 * 4 个线程同时渲染（§4.4、§12.5）。{@link ContentProvider} 由 Spring 注入子类的实例字段。
 */
abstract class AbstractQueryTag implements TagHandler {

    /** 预解析阶段存下的"这一页要渲染什么"。 */
    protected record BodyState(ContentQuery query, List<ContentItem> rows) {
    }

    /** §6.3 共用参数表里"全部"适用（不含仅 query 的 offset / depth / name / cache）。 */
    static List<ParamSpec> sharedParams() {
        return List.of(
                ParamSpec.of("type", "内容类型 code；all = 不限类型"),
                ParamSpec.of("category", "分类 id 或 slug；all = 不限栏目"),
                ParamSpec.boolOf("includeChildren", 1, "分类查询是否含子分类"),
                ParamSpec.of("tag", "标签 slug，多值用 | 分隔（多值之间 AND）"),
                ParamSpec.of("author", "作者 slug 或 id；self = 当前条目的作者"),
                ParamSpec.enumOf("of", null, "self | parent：取锚定项的子内容", "self", "parent"),
                ParamSpec.of("relate", "tag | category | field:<code>：与锚定项相关的内容"),
                ParamSpec.enumOf("exclude", null, "self：排除锚定项自身", "self"),
                ParamSpec.of("where", "字段过滤，语法见 §2.5（code:op:value，逗号 AND）"),
                ParamSpec.of("keyword", "标题模糊匹配（纯字面量）"),
                ParamSpec.boolOf("top", 0, "仅置顶"),
                ParamSpec.boolOf("recommend", 0, "仅推荐"),
                ParamSpec.enumOf("status", "PUBLISHED", "PUBLISHED（默认）/ any：隐含有时间窗",
                        "PUBLISHED", "any"),
                ParamSpec.of("orderby", "内置字段或 indexed 的自定义字段 code，可多字段"),
                ParamSpec.enumOf("order", null, "单字段排序简写", "asc", "desc"),
                ParamSpec.intOf("row", 0, "每页 / 条数"));
    }

    /** {@code {cms:query}} 的完整参数表：共用参数 + §6.3 表里"仅 query"的那四个。 */
    static List<ParamSpec> queryParams() {
        List<ParamSpec> params = new ArrayList<>(sharedParams());
        params.add(ParamSpec.intOf("offset", 0, "跳过 N 条（做第二块列表）"));
        params.add(ParamSpec.intOf("depth", 1, ">1 时层级内容每项预加载 children"));
        params.add(ParamSpec.of("name", "给查询命名，结果元信息进 query.<name>"));
        params.add(ParamSpec.intOf("cache", 0, "秒；同一页面内同名同参查询复用"));
        return List.copyOf(params);
    }

    /** 子类持有的取数出口（Spring 注入）。 */
    protected abstract ContentProvider provider();

    @Override
    public boolean queryTag() {
        return true;
    }

    /** {@code row} 参数的缺省值；{@code typeCode} 是这次查询的类型（{@code type='all'} 时也是 {@code all}）。 */
    protected abstract int rowDefault(String typeCode);

    /** 这个标签在当前页面上是不是分页主体（§4.5 口径表）。 */
    protected abstract boolean paginates(RenderContext ctx);

    /** 这个标签**声明**的参数名；没声明的参数按"没写"处理。 */
    protected abstract List<String> acceptedParams();

    /** 这个标签的渲染是不是按 {@code {cms:list}} 的缺省规则（§7.2.1 的分类索引页）。 */
    protected boolean listPageDefaults() {
        return false;
    }

    /* ---------------- 预解析 ---------------- */

    @Override
    public void prepare(TagNode node, RenderContext ctx) {
        CurrentMarks.requireAllowed(this, node, ctx);
        ContentQuery query = resolve(node, ctx);
        if (paginates(ctx)) {
            prepareAsBody(node, ctx, query);
        } else {
            prepareAsQuery(node, ctx, query);
        }
    }

    /** 解析参数表；{@code row} 缺省值由子类按类型给。 */
    protected ContentQuery resolve(TagNode node, RenderContext ctx) {
        ContentProvider provider = TagSupport.provider(provider(), node, ctx);
        return QueryParams.resolve(node, ctx, provider,
                rowDefault(QueryParams.typeCodeOf(node, ctx)),
                TagSupport.anchor(ctx, provider, node), listPageDefaults(), false, acceptedParams());
    }

    /** 分页主体：只查当前页，算总页数，填 {@code page} 作用域（§5.6）。 */
    private void prepareAsBody(TagNode node, RenderContext ctx, ContentQuery query) {
        ContentProvider provider = TagSupport.provider(provider(), node, ctx);
        // 页号必须在 fill() 之前读：fill 会把 pageNo 写成"这一页的页号"，
        // 而这一页的页号就是派生页计划注入的那个（§5.4 第 5 步：只覆盖 page.pageNo 重渲染）
        int pageNo = TagSupport.pageNo(ctx);
        long totalCount = provider.count(query);
        Pagination pagination = Pagination.of(pageNo, totalCount, Math.max(query.row(), 0),
                PaginationKind.LIST);

        // 只取这一页：offset = (pageNo - 1) * row，派生页因此渲染出不同内容
        ContentQuery pageQuery = pageQuery(query, pageNo);
        List<ContentItem> rows = provider.query(pageQuery).rows();
        pagination.fill(ctx, ctx.pageType(), TagSupport.currentUrl(ctx, pageNo));
        ctx.putNodeState(node, new BodyState(pageQuery, rows));
    }

    /**
     * 这一页要查什么：{@code offset = (pageNo - 1) * row}。
     *
     * <p>两个调用点（{@link #prepareAsBody} 与 {@link #renderBody}）共用它，因此
     * **派生页只覆盖 {@code page.pageNo} 重渲染**这条口径只有一处实现（§5.4 第 5 步）。
     * 页号从 {@code page.pageNo} 读——它是页面计划在渲染前放好的，标签自己不存任何页号状态。
     */
    private static ContentQuery pageQuery(ContentQuery query, int pageNo) {
        int row = Math.max(query.row(), 0);
        int offset = row <= 0 ? 0 : (pageNo - 1) * row;
        return query.copy().offset(offset).row(row);
    }

    /** 非主体的顶层查询：把结果元信息注册进 {@code query.<name>}（§6.3）。 */
    private void prepareAsQuery(TagNode node, RenderContext ctx, ContentQuery query) {
        String name = node.arg("name");
        if (name == null || name.isBlank()) {
            return;
        }
        checkNameInLoop(node, ctx);
        // name 重复由 §4.5 第 16 条在编译期报 E2010；这是渲染期的最后一道
        if (ctx.hasNamedQuery(name)) {
            throw PublishException.error(PublishErrorCode.E2010,
                    "查询名 " + name + " 重复使用", node.sourcePath(), node.lineNo(),
                    "query." + name + " 已经注册过；本页已注册的查询名有 "
                            + String.join(" ", ctx.namedQueries()),
                    "换一个查询名，或删掉重复的那个");
        }
        ContentProvider provider = TagSupport.provider(provider(), node, ctx);
        ContentProvider.QueryResult result = provider.query(query);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("empty", result.empty());
        meta.put("hasResults", !result.empty());
        meta.put("totalCount", result.totalCount());
        meta.put("pageSize", result.pageSize());
        // query.<name>.rows.<i>.<字段> 的下标访问靠它（§6.3 v2.2 新增）
        meta.put("rows", result.rows());
        ctx.putNamedQuery(name, meta);
    }

    /* ---------------- 渲染 ---------------- */

    /** 分页主体：渲染当前页的迭代项（页号在 {@link #prepare} 里已经落进 {@code page}）。 */
    protected void renderBody(TagNode node, RenderContext ctx, TemplateRenderer renderer,
                              StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);
        Object state = ctx.nodeState(node);
        List<ContentItem> rows;
        String fieldType;
        if (state instanceof BodyState body) {
            rows = body.rows();
            fieldType = body.query().crossType() ? null : body.query().typeCode();
        } else {
            // 没拿到预解析结果（写在循环体内，或 nodeState 取不回来）：
            // 按 page.pageNo 现算一次——**不退回第 1 页**，否则派生页会渲染出重复内容
            ContentQuery query = resolve(node, ctx);
            ContentQuery pageQuery = pageQuery(query, TagSupport.pageNo(ctx));
            rows = TagSupport.provider(provider(), node, ctx).query(pageQuery).rows();
            fieldType = query.crossType() ? null : query.typeCode();
        }
        renderRows(node, rows, fieldType, ctx, renderer, out);
    }

    /** 非主体的查询：结果为空时标签自身不渲染（§5.4 约束三的正确行为）。 */
    protected void renderQuery(TagNode node, RenderContext ctx, TemplateRenderer renderer,
                               StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);
        String name = node.arg("name");
        ContentQuery query = resolve(node, ctx);
        List<ContentItem> rows;
        if (name != null && !name.isBlank()) {
            checkNameInLoop(node, ctx);
            rows = ctx.hasNamedQuery(name)
                    // 顶层命名查询：用预解析的结果，保证"列表上方判 empty"与"列表本身"是同一批数据（§5.4）
                    ? TagSupport.namedRows(ctx, name)
                    : TagSupport.provider(provider(), node, ctx).query(query).rows();
        } else {
            rows = TagSupport.provider(provider(), node, ctx).query(query).rows();
        }
        renderRows(node, rows, query.crossType() ? null : query.typeCode(), ctx, renderer, out);
    }

    /**
     * §5.4 约束三：循环体内的查询不可命名 → E2011。
     *
     * <p><b>谁报这条</b>：编译期由校验器报（它按"节点在不在循环体内"遍历 AST，能查到）；
     * 这里是渲染期的兜底——写在循环体里的标签只在循环体内被渲染，那时 {@code ctx.inLoop()} 为真。
     */
    private static void checkNameInLoop(TagNode node, RenderContext ctx) {
        if (!ctx.inLoop()) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E2011,
                "循环内的查询不能命名", node.sourcePath(), node.lineNo(),
                "{cms:" + node.name() + " name='" + node.arg("name") + "'} 位于循环体内，"
                        + "它的 empty 在标签外访问不到",
                "去掉 name 参数；需要占位文案时把这个查询提到顶层并命名（§5.4 约束三）");
    }

    /** 逐个压栈渲染：迭代项补 {@code current} / {@code class}、站点默认封面与 5 个位置字段（§5.5、§6.2、§6.3）。 */
    private static void renderRows(TagNode node, List<ContentItem> rows, String fieldType,
                                   RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        long currentId = ctx.currentEntryId();
        int count = rows.size();
        // 传被查询类型的 code：渲染期靠它查自定义字段的声明（raw / fieldType / options）
        ctx.enterLoop();
        try {
            int index = 0;
            for (ContentItem item : rows) {
                Map<String, Object> frame = TagSupport.iterationItem(item, currentId, ctx);
                // §6.2 的 5 个计数/位置字段，口径与 {cms:foreach} 的 ForeachTag.frameOf 完全一致：
                // 编译期的作用域走查早就把它们算进查询体了（ScopeWalker 给 LIST/QUERY 帧 union 了
                // LOOP_KEYS），渲染期不补就会出现"校验通过、一渲染就 E1004 字段 isLast 不存在"
                // 这种自相矛盾——而 {cms:if field='isLast'} 正是逗号、分隔符这类"最后一个不要尾巴"
                // 场合唯一能用的写法。
                frame.put("index", index + 1);
                frame.put("index0", index);
                frame.put("isFirst", index == 0);
                frame.put("isLast", index == count - 1);
                frame.put("count", count);
                ctx.pushAnonymous(frame, fieldType);
                try {
                    renderer.renderNodes(node.bodyNodes(), ctx, out);
                } finally {
                    ctx.popAnonymous();
                }
                index++;
            }
        } finally {
            ctx.exitLoop();
        }
    }
}
