package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.Resolution;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code {cms:if}}（static-publish.md §6.2、§3.7 裁定一）：
 *
 * <pre>
 * {cms:if field='price'}…{cms:else/}…{/cms:if}
 * </pre>
 *
 * <p>它没有任何"条件语法"——只有一个字段路径。真假判定复用 {@link RenderContext#truthy}
 * （判定顺序写在那一处，别处不许再写第二份）；字段名解析不到是**报错**，不是判假
 * （§3.7 表格第三行）。
 *
 * <p>{@code {cms:else/}} 不是节点，而是编译器在 {@link TagNode#elseIndex} 上记下的位置，
 * 所以这里只按"else 前 / else 后"渲染 {@link TagNode#bodyBeforeElse()} 或
 * {@link TagNode#bodyAfterElse()}。
 */
@Component
public class IfTag implements TagHandler {

    @Override
    public String name() {
        return "if";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(ParamSpec.required("field",
                "字段路径，写法与 [field:xxx/] 完全一致（含具名作用域与多段路径）"));
    }

    /** §6.7 的矩阵第一行：任何页面类型上个数不限。 */
    @Override
    public int maxCount(PageType pageType) {
        return -1;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        Resolution resolution = DefaultTemplateRenderer.resolveOrFail(ctx,
                DefaultTemplateRenderer.splitPath(node.arg("field")), node.sourcePath(), node.lineNo());
        renderer.renderNodes(RenderContext.truthy(resolution.value())
                ? node.bodyBeforeElse() : node.bodyAfterElse(), ctx, out);
    }
}
