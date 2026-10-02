package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.CategoryBrief;
import com.lingjiuw.cms.module.cms.dto.PublicArticleVO;
import com.lingjiuw.cms.module.cms.dto.TagBrief;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.cms.publish.provider.Rows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公开内容接口：只读、仅返回已发布内容，供门户/官网使用。
 *
 * <p>取的是 {@code cms_content} 里内置 {@code article} 类型的内容（§2.6 的一次收敛：
 * 文章不再是独立的一张表）。返回结构与收敛前一致。
 */
@Service
@RequiredArgsConstructor
public class PublicArticleService {

    /** 内置类型：公开接口固定只暴露它（§2.1） */
    private static final String TYPE_ARTICLE = "article";

    private final CmsContentMapper contentMapper;
    private final CmsContentCategoryMapper contentCategoryMapper;
    private final CmsContentTagMapper contentTagMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;

    public PageResult<PublicArticleVO> page(long page, long size, Long categoryId, String keyword) {
        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        Long siteId = SiteContext.siteId();
        long total = contentMapper.countPublicArticleRows(siteId, categoryId, keyword);
        List<CmsContent> rows = contentMapper.selectPublicArticleRows(siteId, categoryId, keyword,
                pageSize, (current - 1) * pageSize);
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), total, current, pageSize);
        }
        List<Long> contentIds = rows.stream().map(CmsContent::getId).toList();
        // 关联批量取：一页一条 SQL，而不是每行两次（旧实现是逐条查分类与标签）
        Map<Long, CategoryBrief> categories = categoriesOf(contentIds);
        Map<Long, List<TagBrief>> tags = tagsOf(contentIds);
        List<PublicArticleVO> records = rows.stream()
                .map(row -> toVO(row, false, categories.get(row.getId()),
                        tags.getOrDefault(row.getId(), List.of())))
                .toList();
        return new PageResult<>(records, total, current, pageSize);
    }

    public PublicArticleVO detail(String slug) {
        // 用 selectList 取首条：同站点同类型下 slug 只在"同一父级"内唯一（§2.3 的两条部分唯一索引），
        // selectOne 命中多条时抛的是 TooManyResultsException，会变成 500 而不是业务提示
        List<CmsContent> matches = contentMapper.selectList(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, SiteContext.siteId())
                .eq(CmsContent::getTypeCode, TYPE_ARTICLE)
                .eq(CmsContent::getSlug, slug)
                .eq(CmsContent::getStatus, "PUBLISHED")
                .orderByAsc(CmsContent::getId));
        if (matches.isEmpty()) {
            throw new BizException("文章不存在或未发布");
        }
        CmsContent content = matches.get(0);
        contentMapper.increaseViewCount(content.getId());
        content.setViewCount(content.getViewCount() == null ? 1 : content.getViewCount() + 1);
        List<Long> contentIds = List.of(content.getId());
        return toVO(content, true, categoriesOf(contentIds).get(content.getId()),
                tagsOf(contentIds).getOrDefault(content.getId(), List.of()));
    }

    private PublicArticleVO toVO(CmsContent content, boolean withContent, CategoryBrief category,
                                 List<TagBrief> tags) {
        return new PublicArticleVO(
                content.getId(), content.getTitle(), content.getSlug(), content.getSummary(),
                withContent ? content.getContent() : null,
                content.getContentFormat(), content.getCover(), content.getViewCount(),
                content.getPublishTime(), category, tags);
    }

    /** 主分类：它决定详情页 URL 与面包屑，公开接口只给这一条（§2.3）。 */
    private Map<Long, CategoryBrief> categoriesOf(List<Long> contentIds) {
        List<Rows.RelationRow> refs = contentCategoryMapper.selectContentCategories(contentIds).stream()
                .filter(ref -> "primary".equals(ref.getDimension()))
                .toList();
        if (refs.isEmpty()) {
            return Map.of();
        }
        // 一次批量取分类，并且只认当前站点的分类：别的站点的 id 在这里就当没有
        Map<Long, CmsCategory> categories = categoryMapper.selectList(Wrappers.<CmsCategory>lambdaQuery()
                        .in(CmsCategory::getId, refs.stream().map(Rows.RelationRow::getRefId).distinct().toList())
                        .eq(CmsCategory::getSiteId, SiteContext.siteId())).stream()
                .collect(Collectors.toMap(CmsCategory::getId, Function.identity()));
        Map<Long, CategoryBrief> result = new HashMap<>();
        for (Rows.RelationRow ref : refs) {
            CmsCategory category = categories.get(ref.getRefId());
            if (category != null && !result.containsKey(ref.getContentId())) {
                result.put(ref.getContentId(),
                        new CategoryBrief(category.getId(), category.getName(), category.getSlug()));
            }
        }
        return result;
    }

    private Map<Long, List<TagBrief>> tagsOf(List<Long> contentIds) {
        List<Rows.RelationRow> refs = contentTagMapper.selectContentTags(contentIds);
        if (refs.isEmpty()) {
            return Map.of();
        }
        // 同上：只取当前站点的标签（selectBatchIds 不带站点条件会把别的站点的标签也查出来）
        Map<Long, CmsTag> tags = tagMapper.selectList(Wrappers.<CmsTag>lambdaQuery()
                        .in(CmsTag::getId, refs.stream().map(Rows.RelationRow::getRefId).distinct().toList())
                        .eq(CmsTag::getSiteId, SiteContext.siteId())).stream()
                .collect(Collectors.toMap(CmsTag::getId, Function.identity()));
        Map<Long, List<TagBrief>> result = new HashMap<>();
        for (Rows.RelationRow ref : refs) {
            CmsTag tag = tags.get(ref.getRefId());
            if (tag != null) {
                result.computeIfAbsent(ref.getContentId(), key -> new ArrayList<>())
                        .add(new TagBrief(tag.getId(), tag.getName(), tag.getSlug()));
            }
        }
        return result;
    }
}
