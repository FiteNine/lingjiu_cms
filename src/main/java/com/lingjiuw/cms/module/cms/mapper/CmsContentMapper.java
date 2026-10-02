package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.publish.provider.ContentRow;
import com.lingjiuw.cms.module.cms.publish.provider.ProviderParams;
import com.lingjiuw.cms.module.cms.publish.provider.Rows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 内容的查询大量依赖公共列与索引表，BaseMapper + LambdaQueryWrapper 足够；
 * 静态发布引擎那批"时间窗 + 栏目 / 标签 + 排序 + 分页"的语句放在
 * {@code mapper/cms/CmsContentMapper.xml}（backend/AGENTS.md：SQL 一律写 XML）。
 */
@Mapper
public interface CmsContentMapper extends BaseMapper<CmsContent> {

    /**
     * 新增内容。{@code data} 与 {@code content_toc} 是 jsonb 列：PostgreSQL 不接受 varchar 的
     * 隐式赋值，所以必须走 XML 的 {@code ::jsonb} 转换（实体上已把这两列排除在内置 insert 之外）。
     *
     * <p>参数就是实体本身（不加 {@code @Param}）：自增 id 由 XML 里的 {@code selectKey}
     * （{@code order="BEFORE"}，取 {@code cms_content_id_seq}）显式回填到 {@code row.id}——
     * 索引与关联表要用它。MyBatis 不会给 XML 里的自定义 insert 自动接 {@code useGeneratedKeys}。
     */
    int insertContent(CmsContent row);

    /** 修改内容；同样显式处理 {@code data} / {@code content_toc} 两个 jsonb 列。 */
    int updateContent(CmsContent row);

    /** 某内容的分类关联（dimension 一起给，主分类排在前面）。 */
    List<Rows.RelationRow> selectContentCategoryRefs(@Param("contentId") Long contentId);

    /** 某内容的标签关联。 */
    List<Rows.RelationRow> selectContentTagRefs(@Param("contentId") Long contentId);

    /** 某类型下的内容条数（SINGLE 类型"至多一条"的校验用；{@code excludeId} 排除自身）。 */
    long countByType(@Param("siteId") Long siteId,
                     @Param("typeCode") String typeCode,
                     @Param("excludeId") Long excludeId);

    /** 当页的行：列表 / 详情 / 邻接共用同一份过滤条件（static-publish.md §6.3）。 */
    List<ContentRow> selectPublishRows(@Param("p") ProviderParams p);

    /** 与 {@link #selectPublishRows} 完全同一份谓词的计数。 */
    long countPublishRows(@Param("p") ProviderParams p);

    /** 排序序列里锚定项之前的那一条（{@code {cms:prenext}} 的"上一篇"）。 */
    List<ContentRow> selectPrevNeighbor(@Param("p") ProviderParams p);

    /** 排序序列里锚定项之后的那一条（"下一篇"）。 */
    List<ContentRow> selectNextNeighbor(@Param("p") ProviderParams p);

    /** 层级后代：{@code p.parentIds} 之下 depth 层以内的全部内容（扁平，按 sort / id）。 */
    List<ContentRow> selectDescendants(@Param("p") ProviderParams p);

    /** 内容自身的祖先链（自下而上查，输出从顶到下）。 */
    List<ContentRow> selectContentAncestors(@Param("siteId") long siteId, @Param("contentId") long contentId);

    /** 每个父内容的直接子内容数（{@code childCount} / {@code hasChildren}）。 */
    List<Rows.ChildCountRow> selectChildCounts(@Param("p") ProviderParams p);

    /** 每个类型下已发布内容的条数（{@code {cms:channel source='type'}} 的 {@code count}）。 */
    List<Rows.TypeCountRow> selectTypeCounts(@Param("p") ProviderParams p);

    /** 年 / 月归档分组计数（{@code mode} 只取 {@code year} / {@code month}）。 */
    List<Rows.ArchiveCountRow> selectArchiveCounts(@Param("p") ProviderParams p,
                                                   @Param("mode") String mode);

    /** facet 取值条与条数（{@code {cms:channel source='facet'}}，§7.4）。 */
    List<Rows.FacetCountRow> selectFacetCounts(@Param("p") ProviderParams p,
                                               @Param("fieldCode") String fieldCode,
                                               @Param("limit") int limit);

    /**
     * 公开接口 {@code /api/public/articles} 的一页行（{@code type_code='article'} 且已发布）。
     *
     * <p>与 {@link #selectPublishRows} 并列而不是复用它：那是发布引擎口径（时间窗 + 索引表
     * 条件 + 模板字段），公开接口要保持收敛前 {@code cms_article} 的行为。
     */
    List<CmsContent> selectPublicArticleRows(@Param("siteId") Long siteId,
                                             @Param("categoryId") Long categoryId,
                                             @Param("keyword") String keyword,
                                             @Param("size") long size,
                                             @Param("offset") long offset);

    /** 与 {@link #selectPublicArticleRows} 同一份谓词的计数。 */
    long countPublicArticleRows(@Param("siteId") Long siteId,
                                @Param("categoryId") Long categoryId,
                                @Param("keyword") String keyword);

    /** 公开详情接口的浏览量自增（见 XML 里的口径说明）。 */
    int increaseViewCount(@Param("id") Long id);
}
