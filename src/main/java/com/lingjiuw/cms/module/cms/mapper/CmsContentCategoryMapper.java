package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.publish.provider.ProviderParams;
import com.lingjiuw.cms.module.cms.publish.provider.Rows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 内容 ↔ 分类：按 content_id 增删改由 BaseMapper 承担；聚合与批量读走 XML。 */
@Mapper
public interface CmsContentCategoryMapper extends BaseMapper<CmsContentCategory> {

    /** 每个分类下已发布内容的条数（{@code CountScope.node} 口径，导航 count 的来源）。 */
    List<Rows.CategoryCountRow> selectCategoryCounts(@Param("p") ProviderParams p);

    /** 一页内容的分类关联（批量取）。 */
    List<Rows.RelationRow> selectContentCategories(@Param("contentIds") List<Long> contentIds);

    /** 内容 ↔ 分类的批量写（保存内容时重建：恰好一行 dimension='primary'）。 */
    int insertBatch(@Param("contentId") Long contentId,
                    @Param("categoryIds") List<Long> categoryIds,
                    @Param("dimension") String dimension);
}
