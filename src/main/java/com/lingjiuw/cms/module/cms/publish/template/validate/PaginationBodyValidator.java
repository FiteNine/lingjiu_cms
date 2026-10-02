package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.BodyKind;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * §4.5 第 8 条 ★：**分页主体数量**与位置 → E3001 / E3002（口径表见 §4.5 末尾，v2.1 定死）。
 *
 * <p>两条死限（其余组合都合法）：
 * <ol>
 *   <li>{@code {cms:list}} 与 {@code {cms:detail}} 各至多 1 个；</li>
 *   <li>二者不能同时是分页主体：{@code {cms:detail}} 只在 {@code paginate_body} 非空时才是，
 *       因此"正文分页 + 模板里有 {@code {cms:list}}" → E3001，文案要求二选一。</li>
 * </ol>
 *
 * <p>另外两条位置约束：
 * <ul>
 *   <li>分页主体不得写在 {@code {cms:if}} 体内 → E3002（§5.4 约束二；写在 include 片段里是允许的，
 *       因为 include 已在编译期展开，展开后唯一性仍可校验）；</li>
 *   <li>循环体内**不存在** {@code {cms:list}} → E3001 的第二种文案（§5.4 约束三）。</li>
 * </ul>
 *
 * <p>各页面类型允许几个"分页主体"由 §4.5 的口径表定，而"这个标签能不能出现在这个页面类型上"
 * 是 §6.7 的矩阵（E3012），两者分工不同、互不重复。
 */
@Component
public final class PaginationBodyValidator implements TemplateValidator {

    private final TagRegistry registry;

    public PaginationBodyValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 8;
    }

    @Override
    public String describe() {
        return "§4.5 第 8 条：分页主体数量与位置（E3001 / E3002）";
    }

    /** 一个分页主体节点 + 它所在的位置。 */
    private record Body(TagNode node, boolean inLoop, ScopeWalker.Scope scope) {
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        List<Body> lists = new ArrayList<>();
        List<Body> details = new ArrayList<>();
        boolean contentPagination = paginatesBody(ctx);
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null) {
                    return;
                }
                // details 无条件收集：§4.5 的死限 1 是"各至多 1 个"，与这一页是否走正文分页无关。
                // contentPagination 只用在死限 2（正文分页与列表分页同现）那一条上。
                if (handler.bodyKind() == BodyKind.LIST) {
                    lists.add(new Body(node, scope.inLoop(), scope));
                } else if (handler.bodyKind() == BodyKind.DETAIL) {
                    details.add(new Body(node, scope.inLoop(), scope));
                }
            }
        });

        // §5.4 约束三：循环体内的 {cms:list} 不存在（分页主体在循环里 = 计划期无解）
        for (Body body : lists) {
            if (body.inLoop) {
                throw PublishException.error(PublishErrorCode.E3001,
                        "循环体内不能有 {cms:list}", body.node.sourcePath(), body.node.lineNo(),
                        "第 " + body.node.lineNo() + " 行的 {cms:list} 在循环体内",
                        "{cms:list} 是页面主体（可分页），循环里的列表用 {cms:query}（§5.4 约束三）");
            }
        }
        // §5.4 约束二：分页主体不得写在 {cms:if} 体内（写在 include 片段里可以，展开后再判）
        for (Body body : concat(lists, details)) {
            if (body.scope.insideIf()) {
                TagNode open = body.scope.enclosingIf();
                throw PublishException.error(PublishErrorCode.E3002,
                        "第 " + body.node.lineNo() + " 行的 {cms:" + body.node.name()
                                + "} 是分页主体，不能写在 {cms:if} 内",
                        body.node.sourcePath(), body.node.lineNo(),
                        "（第 " + open.lineNo() + " 行的 {cms:if}）",
                        "把分页主体移到 {cms:if} 之外——分页计划不能依赖运行期真假（§5.4 约束二）");
            }
        }
        // 死限 1：各至多 1 个
        if (lists.size() > 1) {
            throw tooMany("{cms:list}", lists);
        }
        if (details.size() > 1) {
            throw tooMany("{cms:detail}", details);
        }
        // 死限 2：正文分页与列表分页不能同时是分页主体
        if (contentPagination && !lists.isEmpty()) {
            TagNode detail = details.isEmpty() ? null : details.get(0).node;
            TagNode list = lists.get(0).node;
            // contentPagination 由"页面类型 + paginate_body"决定，与模板里写不写 {cms:detail} 无关：
            // 没有 detail 时不能说"出现了两个分页主体"，更不能报出"第 0 行"
            throw PublishException.error(PublishErrorCode.E3001,
                    detail == null ? "正文分页与本页的 {cms:list} 冲突" : "同一模板中出现了两个分页主体",
                    list.sourcePath(), list.lineNo(),
                    detail == null
                            ? "该详情页类型的 paginate_body 非空（走正文分页），第 " + list.lineNo()
                                    + " 行还有 {cms:list}"
                            : "第 " + detail.lineNo() + " 行的 {cms:detail} 走正文分页，第 "
                                    + list.lineNo() + " 行还有 {cms:list}",
                    "正文分页与列表分页二选一；要同时保留，就把列表放到另一个模板（或去掉 paginate_body）");
        }
    }

    /** 该页面的类型是否走正文分页（{@code paginate_body} 非空，且页面类型是详情页）。 */
    private static boolean paginatesBody(CompileContext ctx) {
        if (ctx.pageType() != PageType.DETAIL && ctx.pageType() != PageType.DPAGE) {
            return false;
        }
        ContentTypeDef def = ctx.provider() == null ? null
                : ctx.typeCode() == null ? null : ctx.provider().type(ctx.typeCode());
        return def != null && def.paginatesBody();
    }

    private static List<Body> concat(List<Body> first, List<Body> second) {
        List<Body> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static PublishException tooMany(String tag, List<Body> bodies) {
        TagNode first = bodies.get(0).node;
        TagNode second = bodies.get(1).node;
        return PublishException.error(PublishErrorCode.E3001,
                "同一模板中出现了多个分页主体标签（第 " + first.lineNo() + " 行、第 " + second.lineNo()
                        + " 行），请只保留一个",
                second.sourcePath(), second.lineNo(),
                "共 " + bodies.size() + " 个 " + tag,
                "一个模板至多 1 个分页主体（§4.5 的口径表）；要出两个列表就把另一个改成 {cms:query}（不分页）");
    }
}
