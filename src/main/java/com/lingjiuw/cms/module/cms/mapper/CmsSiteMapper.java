package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.publish.provider.SiteRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmsSiteMapper extends BaseMapper<CmsSite> {

    /** 系统默认站点：迁移保证有且仅有一个 */
    CmsSite selectDefaultSite();

    /**
     * 静态发布的站点配置（static-publish.md §2.7）：含 §2.7（2）新增的 6 个列。
     * 实体 {@code CmsSite} 也有这些列了，这里仍读进 {@link SiteRow}：只暴露发布所需的列，
     * 不让发布引擎的取数与后台实体耦合。
     */
    SiteRow selectPublishSite(@Param("siteId") long siteId);
}
