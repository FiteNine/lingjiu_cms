package com.lingjiuw.cms.module.cms.publish.provider;

import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuItemMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSitePublishOptionMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;

/**
 * {@link DbContentProvider} 需要的那 12 个 mapper。
 *
 * <p>为什么包一层而不是让 {@code DbContentProvider} 收 12 个构造参数：工厂是唯一的装配点，
 * 这个对象是"数据层要读哪些表"的一张清单——加一个 mapper 只改这里与工厂一处。
 * 它是不可变的，可以在多个站点的 provider 之间共享（mapper 本身是线程安全的代理）。
 */
public final class ProviderMappers {

    private final CmsContentMapper contentMapper;
    private final CmsContentTypeMapper contentTypeMapper;
    private final CmsFieldMapper fieldMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsSiteMapper siteMapper;
    private final CmsSitePublishOptionMapper optionMapper;
    private final CmsMediaMapper mediaMapper;
    private final CmsMenuMapper menuMapper;
    private final CmsMenuItemMapper menuItemMapper;
    private final CmsContentCategoryMapper contentCategoryMapper;
    private final CmsContentTagMapper contentTagMapper;

    public ProviderMappers(CmsContentMapper contentMapper,
                           CmsContentTypeMapper contentTypeMapper,
                           CmsFieldMapper fieldMapper,
                           CmsCategoryMapper categoryMapper,
                           CmsTagMapper tagMapper,
                           CmsSiteMapper siteMapper,
                           CmsSitePublishOptionMapper optionMapper,
                           CmsMediaMapper mediaMapper,
                           CmsMenuMapper menuMapper,
                           CmsMenuItemMapper menuItemMapper,
                           CmsContentCategoryMapper contentCategoryMapper,
                           CmsContentTagMapper contentTagMapper) {
        this.contentMapper = contentMapper;
        this.contentTypeMapper = contentTypeMapper;
        this.fieldMapper = fieldMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.siteMapper = siteMapper;
        this.optionMapper = optionMapper;
        this.mediaMapper = mediaMapper;
        this.menuMapper = menuMapper;
        this.menuItemMapper = menuItemMapper;
        this.contentCategoryMapper = contentCategoryMapper;
        this.contentTagMapper = contentTagMapper;
    }

    public CmsContentMapper contentMapper() {
        return contentMapper;
    }

    public CmsContentTypeMapper contentTypeMapper() {
        return contentTypeMapper;
    }

    public CmsFieldMapper fieldMapper() {
        return fieldMapper;
    }

    public CmsCategoryMapper categoryMapper() {
        return categoryMapper;
    }

    public CmsTagMapper tagMapper() {
        return tagMapper;
    }

    public CmsSiteMapper siteMapper() {
        return siteMapper;
    }

    public CmsSitePublishOptionMapper optionMapper() {
        return optionMapper;
    }

    public CmsMediaMapper mediaMapper() {
        return mediaMapper;
    }

    public CmsMenuMapper menuMapper() {
        return menuMapper;
    }

    public CmsMenuItemMapper menuItemMapper() {
        return menuItemMapper;
    }

    public CmsContentCategoryMapper contentCategoryMapper() {
        return contentCategoryMapper;
    }

    public CmsContentTagMapper contentTagMapper() {
        return contentTagMapper;
    }
}
