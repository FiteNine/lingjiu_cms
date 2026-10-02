package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.dto.CategoryContentCount;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CmsCategoryMapper extends BaseMapper<CmsCategory> {

    /**
     * 各分类下未删除内容数。
     *
     * <p>注意：这条统计不按站点过滤，是每次调用都聚合全表的查询。调用方
     * {@code CategoryService.tree()} 只按本站点分类 id 取值（不会串站点数据），
     * 但数据量上来后需要改成带 {@code siteId} 的版本。
     */
    List<CategoryContentCount> selectContentCounts();
}
