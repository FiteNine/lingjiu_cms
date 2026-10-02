package com.lingjiuw.cms.module.cms.service;

/**
 * 站点初始化播种：新建站点后补齐"每个站点都必须有"的定义数据——内置内容类型
 * （{@code article} / {@code single} / {@code author}，见 static-publish.md §2.1）、
 * 默认导航菜单 {@code main}（§2.4）与默认发布选项（§2.7）。
 *
 * <p>存量站点由迁移的 {@code insert ... select} 补齐，本接口只负责"此后新建的站点"。
 * 由 {@link SiteService#create} 在站点入库之后调用。
 */
public interface SiteBootstrapService {

    /** 为指定站点补齐内置类型、默认菜单与默认发布选项；已存在的不重复插入（幂等）。 */
    void seed(Long siteId);

    /**
     * 站点删除时清掉它的从属数据：内置内容类型、导航菜单（连同菜单项）与发布选项都带
     * {@code site_id}，站点没了它们就是指向已删站点的孤儿行。由 {@link SiteService#delete} 调用。
     */
    void removeSeeded(Long siteId);
}
