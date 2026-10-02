package com.lingjiuw.cms.module.cms.publish.template.ast;

import com.lingjiuw.cms.module.cms.publish.template.PaginationKind;

import java.util.List;

/**
 * 一棵编译好的模板（static-publish.md §4.4）。
 *
 * <p><b>编译产物不可变</b>：因此可以放进 {@code ConcurrentHashMap} 共享，天然线程安全（§4.4）。
 * include 在这里已经**展开完毕**（§5.3），所以节点上带的是片段内的真实路径与行号；
 * {@code path} 保留入口模板的路径，用于缓存 key 与报错。
 *
 * @param path            入口模板的相对路径
 * @param nodes           展开 include 之后的节点树
 * @param namedQueries    模板里出现过的全部命名查询名（注册顺序），供 E2010 查重与
 *                        {@code query.<name>} 的"列出全部已命名查询"（§5.1）
 * @param paginationBody  分页主体节点（{@code {cms:list}} 或走正文分页的 {@code {cms:detail}}）；
 *                        没有分页主体时为 null（§4.5 的"至多一个"已在此收敛为"零或一"）
 * @param paginationKind  分页主体的种类；没有分页主体时为 null
 * @param astVersion      该模板的失效版本号：自身与 include 链上每一段的
 *                        {@code 路径 + mtime + size + sha256}（§4.4 v2.2 定死）。
 *                        同长度修改片段时字节数不变，只靠 size 会漏失效，因此必须含 sha256。
 */
public record TemplateAst(String path, List<Node> nodes, List<String> namedQueries,
                          TagNode paginationBody, PaginationKind paginationKind, String astVersion) {

    public TemplateAst {
        nodes = List.copyOf(nodes);
        namedQueries = List.copyOf(namedQueries);
    }

    public boolean hasPaginationBody() {
        return paginationBody != null;
    }
}
