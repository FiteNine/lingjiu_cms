package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import java.util.List;

/**
 * 模板渲染器（static-publish.md §4.1、§5）。
 *
 * <p><b>必须无状态、可并发</b>：一次发布开 4 个线程（§12.5），全部可变状态都在
 * {@link RenderContext} 里。
 *
 * <p>渲染流程（§5.4 的顺序不能改）：
 * <ol>
 *   <li>预解析：遍历模板，对"不在循环体内"的 {@link TagHandler#preResolve()} 标签调
 *       {@link TagHandler#prepare}——这样写在列表上方的 {@code {cms:if field='page.empty'}}
 *       才有确定行为；</li>
 *   <li>渲染主体：{@code pageNo=1} 渲染主路径；</li>
 *   <li>派生页：同名模板、同一上下文，只覆盖 {@code page.pageNo} 重渲染（期 2 的派生页计划）。</li>
 * </ol>
 */
public interface TemplateRenderer {

    /** 渲染一棵已编译的模板；预解析在第 1 步完成。 */
    void render(TemplateAst ast, RenderContext ctx, StringBuilder out);

    /** 渲染一段节点列表（标签体、条件分支）。 */
    void renderNodes(List<com.lingjiuw.cms.module.cms.publish.template.ast.Node> nodes, RenderContext ctx,
                     StringBuilder out);

    /**
     * 渲染一个 {@code [field:…/]}：按 §5.1 解析路径，按 §5.2 决定是否转义，
     * 按 §2.2 应用 formatter。解析失败按 §10.2 报 E1004 / E1005。
     */
    void renderField(com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode node, RenderContext ctx,
                     StringBuilder out);

    /**
     * 把一个值按 §5.2 输出成字符串（含转义与 formatter）。标签产出 HTML 属性（如 {@code url}）
     * 时也用它，保证"转义只发生在字段输出时"这一条只有一处实现。
     *
     * <p><b>必须带字段声明</b>：§5.2.1 的 {@code raw}（富文本原样输出）、§2.2 的默认输出
     * （{@code summary} 换行转 {@code <br>}、{@code cover} 输出 url）、§2.2 第 4 条的
     * formatter 合法性、{@code format='label'} 的选项表，全都只能从声明里读出来。
     * 声明用 {@link RenderContext#fieldDef(java.util.List)} 取；取不到时按"声明未知"处理
     * （只做语法与"专属参数名"校验，并按需要转义——安全的默认）。
     *
     * @param value      取值
     * @param def        字段声明；null = 声明未知（内置字段表也没收录）
     * @param formatArgs 模板里为该字段写的 formatter 参数
     */
    String format(Object value, FieldDef def, java.util.Map<String, String> formatArgs);

    /**
     * 不带字段声明的简写形式。**只在确实没有声明可查时用**（例如标签自己产出的
     * {@code page.url} / {@code channel.label} 这类非内容字段）；有声明却走这里，
     * 富文本会被转义、{@code format='label'} 会输出存储值。
     *
     * <p><b>没有 {@code raw} 形参</b>：§5.2.1 的 {@code raw} 只能由字段声明决定，
     * 标签处理器无权打开它——否则任何处理用户可控文本的标签都能绕过转义。要原样输出，
     * 只能改走 {@link #format(Object, FieldDef, java.util.Map)} 并传真实的
     * {@link FieldDef}，由声明决定 {@code raw}。
     *
     * <p>合成声明的字段类型是 {@code null}（= 类型未知）：只做语法层校验（参数名封闭清单、
     * formatter 名、单 formatter），跳过"字段类型 × formatter"的合法性校验，输出恒按
     * {@code raw=false} 转义（§5.2.1 的安全默认）。
     *
     * <p><b>为什么另起一个方法名而不是重载 {@code format}</b>：重载要靠第二个形参的类型
     * （{@code FieldDef} 与 {@code String}）区分，调用方传 {@code null} 时两种重载都匹配，
     * 编译期直接报"对 format 的引用不明确"（既有的 {@code format(value, null, args)} 用例就会踩中）。
     */
    default String formatUndeclared(Object value, String formatter, java.util.Map<String, String> formatArgs) {
        FieldDef synthetic = FieldDef.builder(null, "?", null)
                .formatter(formatter)
                .build();
        return format(value, synthetic, formatArgs);
    }
}
