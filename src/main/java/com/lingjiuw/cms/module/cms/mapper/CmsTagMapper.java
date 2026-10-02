package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.dto.TagContentCount;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmsTagMapper extends BaseMapper<CmsTag> {

    /**
     * 各标签关联的未删除内容数。
     *
     * <p>关联表（cms_content_tag）没有 site_id 列，站点维度只能通过连
     * cms_content 补上；否则标签列表页每次都要对整张关联表做 count(distinct)。
     */
    List<TagContentCount> selectContentCounts(@Param("siteId") Long siteId);
}
