package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;

/**
 * 页面类型 → 站点发布选项的映射（static-publish.md §2.7 的选项表、§7.3 第（3）条、§4.5 第 18 条）。
 *
 * <p>为什么单独一个类：同一份映射有两个消费者——{@link TemplateLookupRules}（§7.3 第（3）条
 * "站点选项关闭时不开工"：{@code page.archive=0} 时不会去找 {@code archive_list.html}，
 * 也不会因为缺它而报错）与 {@link DisabledPageTypeWarner}（W5001 警告）。两处各写一份必然漂移。
 *
 * <p>映射里**没有 {@code LIST}**：{@code page.category} 管的是"分类索引页"，
 * 而 {@code LIST} 有两种来源（类型列表页 / 分类索引页，§7.2.1），编译上下文里没有区分它们的字段。
 * 分不清就不猜——猜错会把"类型列表页"当成被关掉，让 W5001 变成噪音。
 */
final class PageTypeOptions {

    private PageTypeOptions() {
    }

    /**
     * 该页面类型对应的发布选项 code。
     *
     * @param typeCode 当前页面的内容类型 code；{@code DETAIL} 上要靠它区分作者页（{@code page.author}）
     * @return 选项 code；没有对应选项时返回 null（首页、类型列表页、普通详情页、单页）
     */
    static String optionCode(PageType pageType, String typeCode) {
        return switch (pageType) {
            case TAGPAGE -> "page.tag";
            case TAGLIST -> "page.taglist";
            case ARCHIVE -> "page.archive";
            case FACET -> "page.facet";
            case SEARCH -> "page.search";
            case FEED -> "page.feed";
            // 作者是内置内容类型 author（§2.1），它的页面就是 DETAIL
            case DETAIL, DPAGE -> "author".equals(typeCode) ? "page.author" : null;
            default -> null;
        };
    }

    /**
     * 站点是否关掉了这一类页面。默认值按 §2.7 的选项表逐项取：{@code page.archive} /
     * {@code page.facet} 的契约默认值是 {@code 0}（关闭），其余映射选项默认 {@code 1}。
     *
     * <p>这里必须自己带上默认值：{@link SiteConfig#flag(String, boolean)} 只认调用方传进去的
     * 默认值。用"一律默认开"会让未配置时归档页 / 筛选页被当成开启，与
     * {@code SitePlanner} 的 {@code site.flag("page.archive", false)} 判断相反（§7.3 第（3）条）。
     */
    static boolean disabled(PageType pageType, String typeCode, SiteConfig site) {
        String option = optionCode(pageType, typeCode);
        if (option == null || site == null) {
            return false;
        }
        boolean enabledByDefault = !"page.archive".equals(option) && !"page.facet".equals(option);
        return !site.flag(option, enabledByDefault);
    }
}
