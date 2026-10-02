package com.lingjiuw.cms.module.cms.publish.provider;

import lombok.Data;

/**
 * {@code cms_site} 的一行，含 §2.7 的发布相关列（{@code lang} / {@code default_cover} /
 * {@code og_image} / {@code theme} / {@code protocol} / {@code statistics_code}）。
 *
 * <p>为什么单开一个行类型而不是用实体 {@code CmsSite}：这是**发布作用域**要读的列的清单——
 * XML 里显式写列名（{@code selectPublishSite}），读进来的就是站点配置要的那一份，
 * 发布逻辑因此不与业务实体的字段增删耦合（实体加了列也不必回头改这里）。
 */
@Data
public class SiteRow {

    private Long id;
    private String name;
    private String code;
    private String domain;
    private String logo;
    private String description;
    private String keywords;
    private String seoDescription;
    private String rootDir;
    private String icp;
    private String contactPhone;
    private String contactEmail;
    private String lang;
    private String defaultCover;
    private String ogImage;
    private String theme;
    private String protocol;
    private String statisticsCode;
}
