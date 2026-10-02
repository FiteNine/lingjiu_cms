package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.dto.TagContentCount;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CmsTagMapper extends BaseMapper<CmsTag> {

    /** 各标签关联的未删除内容数 */
    List<TagContentCount> selectContentCounts();
}
