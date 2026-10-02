package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.StatsVO;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.system.entity.SysUser;
import com.lingjiuw.cms.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final CmsContentMapper contentMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsMediaMapper mediaMapper;
    private final SysUserMapper userMapper;

    public StatsVO stats() {
        Long siteId = SiteContext.siteId();
        Long contentTotal = contentMapper.selectCount(
                Wrappers.<CmsContent>lambdaQuery().eq(CmsContent::getSiteId, siteId));
        Long contentPublished = contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getStatus, "PUBLISHED"));
        Long contentDraft = contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, siteId)
                .eq(CmsContent::getStatus, "DRAFT"));
        Long categoryTotal = categoryMapper.selectCount(
                Wrappers.<CmsCategory>lambdaQuery().eq(CmsCategory::getSiteId, siteId));
        Long tagTotal = tagMapper.selectCount(
                Wrappers.<CmsTag>lambdaQuery().eq(CmsTag::getSiteId, siteId));
        Long mediaTotal = mediaMapper.selectCount(
                Wrappers.<CmsMedia>lambdaQuery().eq(CmsMedia::getSiteId, siteId));
        // 用户是系统级的，不随站点走
        Long userTotal = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery());

        // searchCount = false：最近内容只要前 5 条，total 用不到，不必多跑一次 COUNT
        List<StatsVO.RecentContent> recent = contentMapper.selectPage(new Page<>(1, 5, false),
                        Wrappers.<CmsContent>lambdaQuery()
                                .eq(CmsContent::getSiteId, siteId)
                                .orderByDesc(CmsContent::getId))
                .getRecords().stream()
                .map(content -> new StatsVO.RecentContent(content.getId(), content.getTypeCode(),
                        content.getTitle(), content.getStatus(), content.getCreateTime(),
                        content.getAuthorName()))
                .toList();

        return new StatsVO(contentTotal, contentPublished, contentDraft,
                categoryTotal, tagTotal, mediaTotal, userTotal, recent);
    }
}
