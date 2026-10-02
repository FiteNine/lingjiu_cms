package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 某个标签是不是"页面主体"（§4.5 口径表）。一个模板**至多一个**分页主体：
 * {@code {cms:list}} 至多一个、{@code {cms:detail}} 至多一个，且二者不能同时是分页主体。
 */
public enum BodyKind {

    /** 不是页面主体。 */
    NONE,

    /** {@code {cms:list}}：列表分页主体（有 {@code pageNo=2..N} 时它就是分页主体）。 */
    LIST,

    /**
     * {@code {cms:detail}}：只有在类型的 {@code paginate_body} 非空时才是**正文分页**主体；
     * 在单页（{@code SINGLE}）上它只是"显式取出当前条目"，不产生第 2 页（§4.5 口径表、§6.3）。
     */
    DETAIL
}
