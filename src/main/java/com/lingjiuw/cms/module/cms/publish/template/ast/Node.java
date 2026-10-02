package com.lingjiuw.cms.module.cms.publish.template.ast;

/**
 * 模板 AST 节点（static-publish.md §4.1）。每个节点记录**起始行号**与**来源模板路径**：
 * include 在编译期展开（§5.3），展开后节点仍保留片段内的真实路径与行号，
 * 因此报错永远指向片段本身，而不是包含它的模板。
 */
public sealed interface Node permits TextNode, TagNode, FieldNode {

    /** 节点起始行号，从 1 开始（§4.2 要求每个节点记录起始行号）。 */
    int lineNo();

    /** 该节点所在模板的相对路径（展开 include 后指向片段文件）。 */
    String sourcePath();
}
