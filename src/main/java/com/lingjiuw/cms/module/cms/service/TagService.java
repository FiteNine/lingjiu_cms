package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.TagContentCount;
import com.lingjiuw.cms.module.cms.dto.TagSaveRequest;
import com.lingjiuw.cms.module.cms.dto.TagVO;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final CmsTagMapper tagMapper;
    private final CmsContentTagMapper contentTagMapper;

    public List<TagVO> list(String keyword) {
        List<CmsTag> tags = tagMapper.selectList(Wrappers.<CmsTag>lambdaQuery()
                .eq(CmsTag::getSiteId, SiteContext.siteId())
                .like(StringUtils.hasText(keyword), CmsTag::getName, keyword)
                .orderByAsc(CmsTag::getId));
        Map<Long, Long> counts = tagMapper.selectContentCounts(SiteContext.siteId()).stream()
                .collect(Collectors.toMap(TagContentCount::tagId, TagContentCount::count, (a, b) -> a));
        return tags.stream()
                .map(tag -> new TagVO(tag.getId(), tag.getName(), tag.getSlug(),
                        counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    public void create(TagSaveRequest request) {
        checkSlugUnique(request.slug(), null);
        CmsTag tag = new CmsTag();
        tag.setName(request.name());
        tag.setSlug(request.slug());
        tag.setSiteId(SiteContext.siteId());
        tagMapper.insert(tag);
    }

    public void update(Long id, TagSaveRequest request) {
        CmsTag tag = tagMapper.selectOne(Wrappers.<CmsTag>lambdaQuery()
                .eq(CmsTag::getId, id)
                .eq(CmsTag::getSiteId, SiteContext.siteId()));
        if (tag == null) {
            throw new BizException("标签不存在或已被删除");
        }
        checkSlugUnique(request.slug(), id);
        tag.setName(request.name());
        tag.setSlug(request.slug());
        tagMapper.updateById(tag);
    }

    @Transactional
    public void delete(Long id) {
        // 逻辑删除的影响行数就是存在性判定：不用先查一次，也就没有查询与删除之间的竞态
        if (tagMapper.delete(Wrappers.<CmsTag>lambdaQuery()
                .eq(CmsTag::getId, id)
                .eq(CmsTag::getSiteId, SiteContext.siteId())) == 0) {
            throw new BizException("标签不存在或已被删除");
        }
        // 标签的关联行跟着逻辑删（@TableLogic 会把 delete 变成 update ... deleted = 1）
        contentTagMapper.delete(Wrappers.<CmsContentTag>lambdaQuery().eq(CmsContentTag::getTagId, id));
    }

    /** 标签标识在站点内唯一（库里有部分唯一索引，这里先给出中文提示） */
    private void checkSlugUnique(String slug, Long excludeId) {
        if (!StringUtils.hasText(slug)) {
            return;
        }
        boolean exists = tagMapper.exists(Wrappers.<CmsTag>lambdaQuery()
                .eq(CmsTag::getSiteId, SiteContext.siteId())
                .eq(CmsTag::getSlug, slug)
                .ne(excludeId != null, CmsTag::getId, excludeId));
        if (exists) {
            throw new BizException("该站点下已存在相同的标签标识(slug)");
        }
    }
}
