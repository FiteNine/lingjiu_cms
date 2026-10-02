package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.dto.CategoryContentCount;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmsCategoryMapper extends BaseMapper<CmsCategory> {

    /**
     * 各分类下未删除内容数。
     *
     * <p>关联表（cms_content_category）没有 site_id 列，站点维度只能通过
     * 连 cms_content 补上；否则每次打开分类树都要对整张关联表做一次
     * count(distinct)，与页面实际只展示本站点分类的事实不符。
     */
    List<CategoryContentCount> selectContentCounts(@Param("siteId") Long siteId);
}
