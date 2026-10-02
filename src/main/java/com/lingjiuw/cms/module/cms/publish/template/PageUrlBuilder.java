package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 分页 URL 的出口（static-publish.md §5.6、§7.1）。
 *
 * <p>为什么是接口而不是实现：分页 URL 由**该页面的 URL 规则**决定（列表页是
 * {@code list_url_pattern} 或站点选项 {@code url.list}，详情页上的列表分页是
 * {@code detail_url_pattern} 的 {@code {n}} 形态），而 URL 规则属于页面计划（期 2）。
 * 期 1 的渲染器只知道"第 1 页不含 {@code {n}}、第 2..N 页含 {@code {n}}"
 * （§5.6 的规范形态），具体怎么拼由计划期注入。
 *
 * <p><b>第 1 页永远是"不含 {@code {n}}"的那个形态</b>（{@code /news/} 而不是
 * {@code /news/1.html}），避免"同一内容两个 URL"。
 */
public interface PageUrlBuilder {

    /** 第 1 页的 URL。 */
    String firstPageUrl();

    /**
     * 第 {@code pageNo} 页的 URL。
     *
     * @param pageNo 页号，从 2 开始；实现应对 {@code <= 1} 一律返回 {@link #firstPageUrl()}
     *               （第 1 页的形态不含 {@code {n}}，见上），不要拼出 {@code /page-0/} 这类脏 URL
     */
    String pageUrl(int pageNo);

    /** 只支持单页的页面（{@code {n}} 不存在）：任何页号都返回第一页。 */
    static PageUrlBuilder single(String url) {
        return new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return url;
            }

            @Override
            public String pageUrl(int pageNo) {
                return url;
            }
        };
    }
}
