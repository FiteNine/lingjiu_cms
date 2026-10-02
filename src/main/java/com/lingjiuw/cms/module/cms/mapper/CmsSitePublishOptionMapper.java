package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmsSitePublishOptionMapper extends BaseMapper<CmsSitePublishOption> {

    /** 按站点播种发布选项：已存在同 option_code 的（未删）选项则跳过 */
    int seedDefaults(@Param("siteId") Long siteId,
                     @Param("options") List<CmsSitePublishOption> options);
}
