package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsMenuItem;
import org.apache.ibatis.annotations.Mapper;

/** 纯明细表：按 menu_id 读取与增删改由 BaseMapper 承担，不需要 XML。 */
@Mapper
public interface CmsMenuItemMapper extends BaseMapper<CmsMenuItem> {
}
