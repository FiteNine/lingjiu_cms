package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.publish.provider.ProviderParams;
import com.lingjiuw.cms.module.cms.publish.provider.Rows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 内容 ↔ 标签：按 content_id 增删改由 BaseMapper 承担；{@code {cms:tagnav}} 的取数走 XML。 */
@Mapper
public interface CmsContentTagMapper extends BaseMapper<CmsContentTag> {

    /**
     * 标签与内容数（{@code typeCode} 为空 / all 时按标签 id 分组 = 跨类型按 slug 合并）。
     *
     * @param orderby  {@code count}（默认）/ {@code name} / {@code sort}
     * @param minCount 只输出内容数 ≥ N 的标签
     * @param limit    0 = 不限条数（{@code row} 参数）
     */
    List<Rows.TagCountRow> selectTagCounts(@Param("p") ProviderParams p,
                                           @Param("orderby") String orderby,
                                           @Param("minCount") int minCount,
                                           @Param("limit") int limit);

    /**
     * 一页内容的标签关联（批量取）。
     *
     * <p>前置条件：{@code contentIds} 非空——空集合会让 XML 的 {@code <foreach>} 生成
     * {@code in ()} 这样的非法 SQL。调用方（{@code PublicArticleService.tagsOf} 等）已判空。
     */
    List<Rows.RelationRow> selectContentTags(@Param("contentIds") List<Long> contentIds);

    /**
     * 内容 ↔ 标签的批量写（保存内容时重建）。
     *
     * <p>前置条件：{@code tagIds} 非空且不含重复元素——空集合会让 XML 的 {@code <foreach>}
     * 生成 {@code insert ... values} 后没有内容的非法 SQL，重复值会撞
     * {@code uk_cms_content_tag(content_id, tag_id)}。调用方（{@code ContentService.syncRelations}）
     * 已用 {@code distinct(...)} 过滤。
     */
    int insertBatch(@Param("contentId") Long contentId, @Param("tagIds") List<Long> tagIds);
}
