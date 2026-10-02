package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 3 条 ★：块配对、{@code {cms:else/}} 位置、{@code {/cms:x}} 名称 → E1003。
 *
 * <p>本条校验被拆在三处，各有归属，互不重复：
 * <ul>
 *   <li><b>{@code {/cms:x}} 名称与块配对</b>（"「{@code {/cms:if}} 与最近的 {@code {cms:list}} 不匹配"）：
 *       解析器持有显式栈，只有它知道模板里写的是哪种收尾，报错在解析期；</li>
 *   <li><b>{@code {cms:else/}} 的位置</b>（只能是 {@code {cms:if}} 的直接子节点、同一 if 内最多一个）：
 *       {@code TemplateCompiler} 在 **include 展开之后**统一绑定（§5.3："片段被单独编译时不报位置错"），
 *       报错同样在编译期；</li>
 *   <li><b>写法与标签声明是否相符</b>（{@code body == null} 是自闭合写法，见 {@link TagNode}）：
 *       本类。把 {@code {cms:include …}…{/cms:include}} 这种"标签体写在自闭合标签上"拦下来，
 *       而不是静默忽略标签体。</li>
 * </ul>
 */
@Component
public final class StructureValidator implements TemplateValidator {

    private final TagRegistry registry;

    public StructureValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public String describe() {
        return "§4.5 第 3 条：块配对 / else 位置 / 写法与声明（E1003）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                if ("else".equals(node.name())) {
                    // 正常路径下走不到这里：else 的位置约束由 TemplateCompiler.bindList 在展开之后
                    // 统一处理（它负责摘除并记 elseIndex，位置错当场报 E1003，§5.3）。
                    // 这里只兜住"手工构造的 AST"（例如别的测试直接 new TagNode("else", …)）。
                    throw PublishException.error(PublishErrorCode.E1003,
                            "{cms:else/} 位置错", node.sourcePath(), node.lineNo(),
                            "第 " + node.lineNo() + " 行的 {cms:else/} 不在任何 {cms:if} 的直接子节点里",
                            "{cms:else/} 只能作为 {cms:if} 的直接子节点（§3.5 裁定四）");
                }
                TagHandler handler = registry.handler(node.name());
                if (handler == null) {
                    return; // 未知标签由第 1 条报 E1001
                }
                if (handler.needsBody() && node.selfClosing()) {
                    throw PublishException.error(PublishErrorCode.E1003,
                            "{cms:" + node.name() + "} 必须带标签体", node.sourcePath(), node.lineNo(),
                            "第 " + node.lineNo() + " 行写的是自闭合形态 {cms:" + node.name() + "/}",
                            "写成块标签：{cms:" + node.name() + " …}…{/cms:" + node.name() + "}");
                }
                if (!handler.needsBody() && !node.selfClosing()) {
                    throw PublishException.error(PublishErrorCode.E1003,
                            "{cms:" + node.name() + "} 不能写标签体", node.sourcePath(), node.lineNo(),
                            "第 " + node.lineNo() + " 行写了 {cms:" + node.name() + " …}…{/cms:"
                                    + node.name() + "}",
                            "{cms:" + node.name() + "} 是自闭合标签，写法是 {cms:" + node.name() + " …/}");
                }
            }
        });
    }
}
