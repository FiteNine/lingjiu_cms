package com.lingjiuw.cms.module.cms.publish.template.ast;

/**
 * 文本节点：原样输出，**永不转义**（§5.2.1）。
 * §3.4 的字面量转义（{@code \{cms:}} / {@code \[field:}）在词法阶段已经还原成普通文本。
 */
public record TextNode(String text, int lineNo, String sourcePath) implements Node {
}
