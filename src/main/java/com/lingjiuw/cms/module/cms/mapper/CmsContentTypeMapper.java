package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmsContentTypeMapper extends BaseMapper<CmsContentType> {

    /** 按站点播种内置类型：已存在同 code 的（未删）类型则跳过，重复调用不会重复插入 */
    int seedBuiltinTypes(@Param("siteId") Long siteId, @Param("types") List<CmsContentType> types);

    /** 新增类型：options 是 jsonb，必须靠 XML 里的 {@code ::jsonb} 写入，BaseMapper.insert 写不了 */
    int insertType(CmsContentType type);

    /** 修改类型：同上，options 只能在这里写；code 与 site_id 是不可变标识，不在 set 列表里 */
    int updateType(CmsContentType type);
}
