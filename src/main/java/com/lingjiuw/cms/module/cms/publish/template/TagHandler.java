package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;

import java.util.List;

/**
 * 一个标签的实现（static-publish.md §4.1 的 {@code TagRegistry} + {@code tag/*.java}）。
 *
 * <p>实现类都是 Spring Bean，{@link TagRegistry} 自动收集——沿用工程既有的
 * "实现接口 → Spring 自动注册"风格。
 *
 * <p><b>纯函数性要求</b>：标签实现**不得持有渲染状态**（§12.5：全量发布开 4 个线程），
 * 一次渲染里的全部状态要么在 {@link RenderContext}，要么在 {@link #prepare} 阶段
 * 通过 {@link RenderContext#putNodeState} 存好。
 * 编译产物（AST）编译后不可变，因此共享是天然线程安全的（§4.4）。
 */
public interface TagHandler {

    /** 标签名，不含 {@code cms:} 前缀，全小写（{@code list} / {@code query} / {@code pagelist}…）。 */
    String name();

    /**
     * 参数表（§3.3）。**未声明的参数一律报 E1002**，报错时列出全部可用参数。
     */
    List<ParamSpec> params();

    /**
     * 是否接受任意额外参数（只有 {@code {cms:include}} 是：其余参数都是片段内的
     * {@code [field:param.<key>/]}，但不得与 6 个保留名同名，§6.2）。
     */
    default boolean openParams() {
        return false;
    }

    /**
     * 是否必须带标签体。块标签（{@code {cms:if}} / {@code {cms:list}}…）为 true，
     * 自闭合标签（{@code {cms:include}} / {@code {cms:pagelist}} / {@code {cms:prenext}}…）为 false。
     * 写法与声明不符 → E1003。
     */
    default boolean needsBody() {
        return true;
    }

    /** 该标签是不是页面主体（§4.5 口径表）；只有 {@code {cms:list}} 与 {@code {cms:detail}} 不是 NONE。 */
    default BodyKind bodyKind() {
        return BodyKind.NONE;
    }

    /**
     * 是否参与预解析（§5.4 第 2 步）：把"不在循环体内"的查询在渲染开始前执行完，
     * 这样写在列表**上方**的 {@code {cms:if field='page.empty'}} 才有确定行为。
     * 只有 {@code list} / {@code query} / {@code detail} 返回 true。
     */
    default boolean preResolve() {
        return false;
    }

    /**
     * 是不是"查询标签"（§6.1 的查询类：{@code list} / {@code query} / {@code detail}）。
     *
     * <p>§4.5 第 6、7、15 条要按 {@code where} / {@code orderby} / {@code relate} / {@code type}
     * 等**共用参数**做校验，校验器靠这个标记找到需要检查的节点，而不是按标签名硬编码——
     * §6.1 的标签总表是封闭清单，硬编码会让"新增查询标签"变成两处修改。
     */
    default boolean queryTag() {
        return false;
    }

    /**
     * §6.7 的"标签 × 页面类型合法性矩阵"：该标签在给定页面类型上的**允许个数上限**。
     *
     * @return {@code -1} = 不限；{@code 0} = 不允许（E3012）；{@code n>0} = 至多 n 个
     */
    int maxCount(PageType pageType);

    /**
     * 该标签**标签体内**迭代项提供哪些字段（§6.3 的"统一产出字段契约"）。
     *
     * <p>为什么需要它：§4.5 第 4 条要求"字段名是否存在于该模板上下文可用的字段集合"，
     * 而 {@code {cms:channel}} / {@code {cms:pagelist}} 这类标签体内的可用字段是**标签自己产出**的
     * （分类项有 {@code slug}/{@code count}、归档项有 {@code year}/{@code month}…）。
     * 返回 null 表示"声明不了"——校验器只能把该帧当开放帧（放行任意字段名），
     * 那正是 E1004 会被漏报的地方，所以**能声明就请声明**。
     *
     * @param pageType 当前页面类型（少数标签的产出随页面类型而变）
     * @return 迭代项字段集合；null = 未知（放行）
     */
    default java.util.Set<String> bodyKeys(PageType pageType) {
        return null;
    }

    /**
     * 预解析阶段：把结果算好并写进 {@link RenderContext}（{@code page} / {@code query.<name>} /
     * {@link RenderContext#putNodeState}）。只有 {@link #preResolve()} 为 true 的标签会被调用，
     * 且每个节点在一次页面渲染里只调用一次。
     */
    default void prepare(TagNode node, RenderContext ctx) {
    }

    /**
     * 渲染阶段。标签体由实现自己调 {@code renderer.renderNodes(...)} 决定渲染几次、怎么渲染；
     * 不要自己拼接标签体的文本。
     */
    void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out);
}
