package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.BodyKind;
import com.lingjiuw.cms.module.cms.publish.template.ContentPaginator;
import com.lingjiuw.cms.module.cms.publish.template.Pagination;
import com.lingjiuw.cms.module.cms.publish.template.PaginationKind;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.QueryParams;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code {cms:detail}} —— 详情页 / 单页的当前内容（static-publish.md §6.3）。
 *
 * <p>三种形态，全在这一处：
 * <ul>
 *   <li><b>{@code DETAIL} 页 + 类型的 {@code paginate_body} 非空</b>：它是**正文分页主体**，
 *       按 {@code <!--cms:page-->} 切 {@code content_html}（§5.2.3），产生第 2..N 页
 *       （{@code detail_url_pattern} 的 {@code {n}} 形态）；</li>
 *   <li><b>{@code DETAIL} 页 + {@code paginate_body} 为空</b>：它不是分页主体，只是"显式取出当前条目"；
 *       此时详情页上可以有一个可分页的 {@code {cms:list}}（§5.6 的"详情页 + 可分页列表"）；</li>
 *   <li><b>{@code SINGLE} 页</b>：无论 {@code paginate_body} 如何，它**都不是分页主体**，
 *       不产生第 2 页（§4.5 口径表）。</li>
 * </ul>
 *
 * <p><b>{@code content} 是当前页正文片段</b>（raw）：正文分页时是切片，不是全文。
 * {@code contentHtml} 与它同源（§5.2.2：两类正文都以 {@code content_html} 为唯一渲染源）。
 *
 * <p>§6.3 的专用产出字段分两类，**不要混**：
 * <ul>
 *   <li>{@code hasPrev} / {@code hasNext} = **按类型 {@code sort_field} 的相邻内容是否存在**
 *       （"供 {@code {cms:if}} 用，配 {@code {cms:prenext}}"）——与 {@code {cms:prenext}} 同源，
 *       走 {@code db().neighbors()}；</li>
 *   <li>{@code pageNo} / {@code totalPages} = 正文分页的位置（同步进 {@code page} 作用域）；
 *       正文分页的"能不能翻"在 {@code page.hasPrev} / {@code page.hasNext} 上（§5.6）。</li>
 * </ul>
 *
 * <p><b>参数表只有 {@code type}</b>（§6.3 的 {@code {cms:detail}} 表格就一行）：它取一条内容，
 * 不取列表，因此 {@code where} / {@code orderby} / {@code relate} 之类对它毫无意义——
 * 声明出来会让"编译通过却毫无影响"变成静默陷阱（§1.3）。
 */
@Component
public class DetailTag extends AbstractQueryTag {

    private final ContentProvider provider;

    public DetailTag(@Nullable ContentProvider provider) {
        this.provider = provider;
    }

    @Override
    public String name() {
        return "detail";
    }

    @Override
    public List<ParamSpec> params() {
        // §6.3 的 {cms:detail} 参数表只有 type 一行（它取一条内容，不取列表）
        return List.of(ParamSpec.of("type", "内容类型 code；缺省 = 当前页面的内容类型"));
    }

    @Override
    protected List<String> acceptedParams() {
        return List.of("type");
    }

    @Override
    protected ContentProvider provider() {
        return db();
    }

    @Override
    public BodyKind bodyKind() {
        return BodyKind.DETAIL;
    }

    @Override
    public boolean preResolve() {
        return true;
    }

    @Override
    public int maxCount(PageType pageType) {
        // §6.7 矩阵：{cms:detail} 只在 DETAIL 与 SINGLE 上 0–1，其余一律 ✗（E3012）
        return switch (pageType.matrixColumn()) {
            case DETAIL, SINGLE -> 1;
            default -> 0;
        };
    }

    @Override
    protected int rowDefault(String typeCode) {
        return 1;   // §6.3：detail 的 row 缺省 = 1（它取一条）
    }

    @Override
    protected boolean paginates(RenderContext ctx) {
        // §4.5 口径表：只有 DETAIL 页（含 DPAGE）且**当前页面类型**的 paginate_body 非空时才是分页主体。
        // SINGLE 页上 {cms:detail} 不是分页主体——"不产生第 2 页"是页面类型说了算的。
        if (ctx.pageType().matrixColumn() != PageType.DETAIL) {
            return false;
        }
        ContentTypeDef def = db().type(ctx.typeCode());
        return def != null && def.paginatesBody();
    }

    /* ---------------- 预解析 ---------------- */

    @Override
    public void prepare(TagNode node, RenderContext ctx) {
        CurrentMarks.requireAllowed(this, node, ctx);
        ContentItem item = currentItem(node, ctx);
        String bodyField = bodyFieldCode(item, ctx);
        boolean paginating = paginates(ctx);
        // 页号必须在 fill() 之前读（§5.4 第 5 步：派生页只覆盖 page.pageNo 重渲染）
        int pageNo = TagSupport.pageNo(ctx);
        // 正文分页只在"是分页主体"时发生；不是主体时 slices 为空，render 里退回整段正文
        ContentPaginator.Split split = paginating
                ? ContentPaginator.split(bodyText(item, bodyField))
                : new ContentPaginator.Split(List.of(), List.of());
        if (paginating) {
            Pagination pagination = Pagination.of(pageNo, split.totalPages(), 1, PaginationKind.CONTENT);
            pagination.fill(ctx, ctx.pageType(), TagSupport.currentUrl(ctx, pageNo));
        }
        ContentProvider.Neighbors neighbors = neighbors(item, ctx);
        // W5002 随节点状态带出去（§5.2.3：不阻断发布，进批次报告）
        ctx.putNodeState(node, new DetailState(item, bodyField, split, pageNo, neighbors));
    }

    /* ---------------- 渲染 ---------------- */

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);
        DetailState state = state(node, ctx);
        Map<String, Object> values = TagSupport.iterationItem(state.item(), ctx.currentEntryId(), ctx);
        boolean paginating = !state.split().slices().isEmpty();
        if (paginating) {
            // content 是**当前页正文片段**（切片），contentHtml 与它同源（§6.3 的产出字段表）
            String slice = state.split().page(state.pageNo());
            values.put("content", slice);
            values.put("contentHtml", slice);
        }
        values.put("pageNo", state.pageNo());
        values.put("totalPages", paginating ? state.split().totalPages() : 1);
        // §6.3：hasPrev / hasNext 是"按类型 sort_field 的相邻内容是否存在"，与 {cms:prenext} 同源
        values.put("hasPrev", state.neighbors().prev() != null);
        values.put("hasNext", state.neighbors().next() != null);

        ContentTypeDef def = db().type(state.item().typeCode());
        ctx.pushAnonymous(values, def == null ? null : def.code());
        try {
            renderer.renderNodes(node.bodyNodes(), ctx, out);
        } finally {
            ctx.popAnonymous();
        }
    }

    /** 取预解析结果；没走预解析（写在循环体内）就现算一次。 */
    private DetailState state(TagNode node, RenderContext ctx) {
        Object state = ctx.nodeState(node);
        if (state instanceof DetailState detail) {
            return detail;
        }
        ContentItem item = currentItem(node, ctx);
        String bodyField = bodyFieldCode(item, ctx);
        int pageNo = TagSupport.pageNo(ctx);
        return new DetailState(item, bodyField,
                paginates(ctx) ? ContentPaginator.split(bodyText(item, bodyField))
                        : new ContentPaginator.Split(List.of(), List.of()),
                pageNo, neighbors(item, ctx));
    }

    /**
     * 这次正文分页产生的 {@code W5002} 警告（§5.2.3 第 2 条：分页符落在块级元素内部）。
     * 渲染器把它并进发布批次报告；测试直接读它。
     */
    public static List<ContentPaginator.Warning> warnings(TagNode node, RenderContext ctx) {
        return ctx.nodeState(node) instanceof DetailState detail
                ? detail.split().warnings() : List.of();
    }

    /* ---------------- 取值 ---------------- */

    /**
     * 当前条目。
     *
     * <p>{@code type} 缺省 = 当前页面的类型。{@code SINGLE} 类型的"当前条目"就是它那唯一一条内容
     * （按类型取，不依赖栈底），因为单页可能在渲染上下文里还带着上一页的条目。
     */
    private ContentItem currentItem(TagNode node, RenderContext ctx) {
        if (!ctx.pageType().hasCurrentEntry()) {
            throw PublishException.error(PublishErrorCode.E3012,
                    "{cms:detail} 只能用在详情页 / 单页", node.sourcePath(), node.lineNo(),
                    "当前页面类型是 " + ctx.pageType() + "，没有当前条目",
                    "标签与页面类型的矩阵见 §6.7；要取列表用 {cms:list} / {cms:query}");
        }
        String type = QueryParams.typeCodeOf(node, ctx);
        if (type == null || "all".equals(type)) {
            throw PublishException.error(PublishErrorCode.E1002,
                    "参数 type 不可用", node.sourcePath(), node.lineNo(),
                    "{cms:detail} 需要知道取哪个类型的内容，当前页面类型 " + ctx.pageType() + " 给不出",
                    "显式写 type='<类型 code>'");
        }
        ContentTypeDef def = db().type(type);
        if (def == null) {
            throw PublishException.error(PublishErrorCode.E2006,
                    "内容类型 " + type + " 不存在", node.sourcePath(), node.lineNo(),
                    "本站没有类型 " + type, "用法见 §6.3 的 {cms:detail} 参数表");
        }
        checkKind(node, ctx, def);
        if (def.kind() == ContentTypeDef.Kind.SINGLE) {
            return singleContent(type);
        }
        ContentItem item = ctx.currentItem();
        if (item != null && item.id() > 0) {
            ContentItem byId = db().content(type, item.id());
            if (byId != null) {
                return byId;
            }
        }
        // 类型对不上时不能把当前条目当成本类型的内容返回：那会让标签声称取 X、实际渲染 Y
        // （后面的 bodyField / neighbors 也都按 Y 走）。typeCode 未知（实现没给）不算对不上。
        if (item != null && (item.typeCode() == null || type.equals(item.typeCode()))) {
            return item;
        }
        return ContentItem.empty();
    }

    /** 单页类型只可能有一条内容（§2.1："全站仅一份、不参与列表"）。 */
    private ContentItem singleContent(String typeCode) {
        ContentQuery query = new ContentQuery().type(typeCode).category("all").status("PUBLISHED");
        return db().query(query).rows().stream().findFirst().orElse(ContentItem.empty());
    }

    /** §4.5 第 15 条：{@code SINGLE} 页面上只能取 {@code SINGLE} 类型（E3010）。 */
    private void checkKind(TagNode node, RenderContext ctx, ContentTypeDef def) {
        if (ctx.pageType() != PageType.SINGLE || def.kind() == ContentTypeDef.Kind.SINGLE) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E3010,
                "single 页面上不能取 " + def.code() + "（" + def.kind() + "）类型的内容",
                node.sourcePath(), node.lineNo(),
                "{cms:detail type='" + def.code() + "'} 出现在 SINGLE 页面上",
                "去掉 type 参数（缺省 = 当前页面的类型），或换一个 SINGLE 类型");
    }

    /**
     * {@code hasPrev} / {@code hasNext}：按类型 {@code sort_field} 的相邻内容（§6.3）。
     * 与 {@code {cms:prenext}} 走同一个 {@code db().neighbors()}，两处不会各算一套。
     */
    private ContentProvider.Neighbors neighbors(ContentItem item, RenderContext ctx) {
        if (item == null || item.id() <= 0) {
            return ContentProvider.Neighbors.NONE;
        }
        String typeCode = item.typeCode() == null ? ctx.typeCode() : item.typeCode();
        if (typeCode == null) {
            return ContentProvider.Neighbors.NONE;
        }
        return db().neighbors(typeCode, item.id(), "type", "all");
    }

    /** 正文切片对象：{@code paginate_body} 指向的字段（默认 {@code content}，§6.3）。 */
    private String bodyFieldCode(ContentItem item, RenderContext ctx) {
        ContentTypeDef def = db().type(item.typeCode() == null ? ctx.typeCode() : item.typeCode());
        if (def == null || def.paginateBody() == null || def.paginateBody().isBlank()) {
            return "content";
        }
        return def.paginateBody();
    }

    private String bodyText(ContentItem item, String fieldCode) {
        Object value = item.get(fieldCode);
        if (value == null && !"content".equals(fieldCode)) {
            value = item.get("content");
        }
        return value == null ? "" : String.valueOf(value);
    }

    /** 预解析结果：当前条目 + 切片字段 + 切分结果 + 页号 + 相邻内容。 */
    private record DetailState(ContentItem item, String bodyField, ContentPaginator.Split split,
                               int pageNo, ContentProvider.Neighbors neighbors) {
    }


    /**
     * 本页真正该用的取数出口：优先 {@link CurrentProvider}（页面计划显式设置的那个），
     * 没有被设置（请求内的预览 / 模板体检）时退回 Spring 注入的实例。
     *
     * <p>Spring 注入进来的是**请求作用域代理**，发布线程池里没有请求，一调用就抛
     * {@code ScopeNotActiveException}——发布路径必须走 {@code CurrentProvider}。
     */
    private ContentProvider db() {
        ContentProvider current = CurrentProvider.current();
        return current != null ? current : provider;
    }

}
