package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import org.apache.ibatis.annotations.Mapper;

/** 字段定义按 (site_id, type_code) 读取，LambdaQueryWrapper 足够；不需要 XML。 */
@Mapper
public interface CmsFieldMapper extends BaseMapper<CmsField> {
}
