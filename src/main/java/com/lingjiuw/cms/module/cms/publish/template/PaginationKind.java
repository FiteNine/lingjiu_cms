package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 分页主体的种类（§5.6 的 {@code page.paginationKind}）。
 * {@code {cms:pagelist}} 只许在 {@link #LIST} 上用，用在 {@link #CONTENT} 上 → E3003。
 */
public enum PaginationKind {

    /** 正文分页：由 {@code <!--cms:page-->} 切 {@code content_html}，不按条数。 */
    CONTENT("content"),

    /** 列表分页：按 {@code row} 切列表。 */
    LIST("list");

    private final String code;

    PaginationKind(String code) {
        this.code = code;
    }

    /** 模板里 {@code [field:page.paginationKind/]} 输出的字面量。 */
    public String code() {
        return code;
    }
}
