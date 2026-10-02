package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmsMenuMapper extends BaseMapper<CmsMenu> {

    /** 按站点播种一个菜单：已存在同 code 的（未删）菜单则跳过 */
    int seedDefaultMenu(@Param("siteId") Long siteId,
                        @Param("code") String code,
                        @Param("name") String name);
}
