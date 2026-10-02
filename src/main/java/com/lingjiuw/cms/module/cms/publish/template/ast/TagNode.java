package com.lingjiuw.cms.module.cms.publish.template.ast;

import java.util.List;
import java.util.Map;

/**
 * 标签节点：块标签 {@code {cms:NAME ARGS}…{/cms:NAME}} 与自闭合标签 {@code {cms:NAME ARGS/}}
 * 共用这一个类型（§3.1 的元素 2 与元素 3）。
 *
 * <p><b>两种写法的区分靠 {@code body} 是不是 null</b>：
 * <ul>
 *   <li>{@code body == null} → 模板里写的是自闭合 {@code {cms:x/}}；</li>
 *   <li>{@code body == List.of()} → 模板里写的是块标签但体是空的 {@code {cms:x}{/cms:x}}。</li>
 * </ul>
 * 不能只看 {@code body.isEmpty()}：那样这两种写法无法区分，而 §4.5 第 3 条要判断
 * "写法与标签声明的 {@code needsBody()} 是否相符"（{@code {cms:include …}…{/cms:include}}
 * 应当报错，而不是静默忽略标签体）。
 *
 * <p>{@code {cms:else/}} 在词法上就是"一个名为 else 的自闭合标签"，它的特殊性完全落在
 * {@code elseIndex} 上（§3.5 裁定四）：编译器把它从 {@code body} 里摘掉，并记下它原本
 * 所处的位置。这样"块内分隔"不需要第 6 种词法元素，位置约束也只需在 {@code if} 的
 * 直接子节点上校验一次。
 *
 * @param name       小写标签名（不含 {@code cms:} 前缀），如 {@code list}
 * @param args       参数表，保持模板里的书写顺序；重名参数在解析期即报 E1002（§3.3）
 * @param body       标签体；<b>自闭合标签为 {@code null}</b>，块标签（哪怕是空体）为空列表
 * @param elseIndex  {@code {cms:else/}} 在 {@code body} 中的位置；没有 else 时为 -1
 */
public record TagNode(String name, Map<String, String> args, List<Node> body, int elseIndex,
                      int lineNo, String sourcePath) implements Node {

    public TagNode {
        // 不能用 Map.copyOf：它的迭代顺序在 JDK 规范里是 "unspecified"，而报错要"贴出第一个
        // 未声明的参数"（§10.3 第 1 条），顺序不确定会让同一个模板每次报不同的参数。
        args = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(args));
        body = body == null ? null : List.copyOf(body);
        // elseIndex 与 body 必须自洽：越界会让 bodyBeforeElse/bodyAfterElse 的 subList 抛
        // IndexOutOfBounds，而"自闭合标签却又带 elseIndex"会让 hasElse() 与两个访问器互相矛盾
        if (elseIndex < -1 || (body == null ? elseIndex >= 0 : elseIndex > body.size())) {
            throw new IllegalArgumentException("非法 elseIndex=" + elseIndex + "（body="
                    + (body == null ? "null" : body.size()) + "，"
                    + sourcePath + ":" + lineNo + "）");
        }
    }

    /** 模板里写的是自闭合形态 {@code {cms:x/}}（含孤立的 {@code {cms:else/}}）。 */
    public boolean selfClosing() {
        return body == null;
    }

    /** 体内的节点（自闭合标签为空列表）。 */
    public List<Node> bodyNodes() {
        return body == null ? List.of() : body;
    }

    /** 体内有 {@code {cms:else/}}（§3.5 裁定四：至多一个）。 */
    public boolean hasElse() {
        return elseIndex >= 0;
    }

    /** {@code {cms:if}} 为真时要渲染的那一段体。 */
    public List<Node> bodyBeforeElse() {
        if (body == null) {
            return List.of();
        }
        return elseIndex < 0 ? body : body.subList(0, elseIndex);
    }

    /** {@code {cms:if}} 为假时要渲染的那一段体；没有 else 时为空列表。 */
    public List<Node> bodyAfterElse() {
        if (body == null || elseIndex < 0) {
            return List.of();
        }
        return body.subList(elseIndex, body.size());
    }

    /** 参数的字面量取值；未声明该参数时返回 null（不做"默认值回填"，默认值由 ParamSpec 提供）。 */
    public String arg(String key) {
        return args.get(key);
    }
}
