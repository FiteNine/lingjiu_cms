package com.lingjiuw.cms.module.cms.publish.provider;

import com.lingjiuw.cms.module.cms.entity.CmsSite;
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
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 取数出口的工厂（static-publish.md §4.1）。
 *
 * <p>为什么需要它：引擎在**发布某一个站点**时要一个 {@code ContentProvider}，而 Spring 的 bean 是
 * 单例——标签实现注入的是"取数出口"这个概念，不是"某个站点的取数出口"。发布服务因此按站点
 * 造实例：{@code provider = factory.forSite(siteId)}，然后整批页面都用它（站点隔离在数据层，
 * 标签层全程不看 siteId）。
 *
 * <p>本类**不依赖 {@code SiteContext}**：定时任务跑在没有 HTTP 请求的线程上（见
 * {@code publish/task/SiteTaskRunner}），那里 {@code SiteContext} 是空的。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DbContentProviderFactory {

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

    /**
     * 12 个 mapper 的装配结果。
     *
     * <p>{@code volatile} 是必须的：{@link #forSite(long)} 会被 HTTP 请求线程与定时发布任务
     * **并发**调用，非 volatile 的无同步 check-then-act 惰性初始化没有 happens-before 保证——
     * 另一个线程可能读到非 null 但尚未安全发布的引用。{@code ProviderMappers} 完全不可变，
     * 因此这里只需 {@code volatile} 就够了（重复构造最多发生一次，且结果等价）。
     */
    private volatile ProviderMappers mappers;

    /** 给某个站点造一个取数出口；同一个发布批次应当复用它（实例内带一次发布的快照缓存）。 */
    public ContentProvider forSite(long siteId) {
        return new DbContentProvider(siteId, mappers());
    }

    /**
     * 按站点实体造取数出口。
     *
     * <p>{@code site} 为 null 或没有 id 时按 0 号站点取数（什么都查不到），但要留一条日志：
     * "传了实体却拿不到 id"与"非请求线程用常量 {@code NO_SITE}"是两件事，前者是调用方的 bug，
     * 静默产出空站点会让排查从"一次发布出了个空站"开始。
     */
    public ContentProvider forSite(CmsSite site) {
        if (site == null || site.getId() == null) {
            log.warn("按站点实体取数出口时缺少站点 id（site={}），退化为站点 0（取不到任何数据）",
                    site == null ? "null" : site.getCode());
            return forSite(0L);
        }
        return forSite(site.getId());
    }

    private ProviderMappers mappers() {
        if (mappers == null) {
            mappers = new ProviderMappers(contentMapper, contentTypeMapper, fieldMapper, categoryMapper,
                    tagMapper, siteMapper, optionMapper, mediaMapper, menuMapper, menuItemMapper,
                    contentCategoryMapper, contentTagMapper);
        }
        return mappers;
    }
}
