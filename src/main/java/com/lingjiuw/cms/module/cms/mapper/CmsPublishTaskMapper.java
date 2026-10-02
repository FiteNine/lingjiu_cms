package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsPublishTask;
import org.apache.ibatis.annotations.Mapper;

/** 发布任务/批次的增删查改由 BaseMapper 承担；原生 SQL 到需要时再加。 */
@Mapper
public interface CmsPublishTaskMapper extends BaseMapper<CmsPublishTask> {
}
