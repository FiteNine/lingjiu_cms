package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsMenu;
import com.lingjiuw.cms.module.cms.entity.CmsMenuItem;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuItemMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSitePublishOptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站点播种的实现。
 *
 * <p>本类里的三份常量表是"默认值"的唯一定义（新建站点走这里），迁移
 * {@code V20261002090011__cms_site_seed.sql} 里写死同样的字面量补齐存量站点。
 * <b>改这里必须同时改那个迁移文件</b>——两处值不一致，会让"老站点"与"新站点"的
 * 初始状态不同，而这类差异在文档里看不出来。
 *
 * <p>三条语句都是 {@code insert ... select ... where not exists}，所以 {@link #seed} 幂等：
 * 站点已经有内置类型 / {@code main} 菜单 / 某个选项时不会重复插入。语句在
 * {@code mapper/cms/*.xml} 里（Java 里不出现 SQL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteBootstrapServiceImpl implements SiteBootstrapService {

    /** 内置类型的列表页每页条数：契约没给默认值，取与 feed.size 一致的 20 */
    private static final int BUILTIN_PER_PAGE = 20;

    /**
     * 内置内容类型（§2.1）。detail_url_pattern 按 §7.1.1 的占位符白名单取值：
     * article 用 §2.1 自己的例子，single 用 /{slug}/，author 就是 §2.1 明写的 /author/{slug}/。
     * list_url_pattern 只给 article，且用 {typeCode} 而不是写死段名，避免与站点选项
     * url.list 的 /{categoryPath}/page-{n}/ 撞车（§7.1.3 的 E4004 冲突检测）。
     */
    private static final List<CmsContentType> BUILTIN_TYPES = List.of(
            builtinType("article", "文章", "CONTENT", "/{categoryPath}/{slug}.html", "/{typeCode}/page-{n}/", 1),
            builtinType("single", "单页", "SINGLE", "/{slug}/", null, 2),
            builtinType("author", "作者", "CONTENT", "/author/{slug}/", null, 3));

    /** 默认导航菜单（§2.4）：只建容器，导航项是站点自己的数据 */
    private static final String DEFAULT_MENU_CODE = "main";
    private static final String DEFAULT_MENU_NAME = "主导航";

    /**
     * 默认发布选项（§2.7 那张 47 行的表，展开为 55 个键）。三处契约不是字面量的地方：
     * <ul>
     *   <li>{@code publish.syncTarget} / {@code media.host} 的默认是"空"，存空串；</li>
     *   <li>{@code publish.threads} 的默认是 {@code min(4, CPU)}，不是常量，也存空串表示
     *       "未设置，由引擎按 min(4, CPU) 算"（§8.3）；</li>
     *   <li>{@code publish.rankCron} 契约写的是描述性的"每小时"，落成等价的 cron {@code 0 * * * *}。</li>
     * </ul>
     * {@code pages.static} / {@code feed.types} / {@code facets.combos} / {@code i18n.alternates} /
     * {@code seo.noindexTypes} 存 JSON 字面量（契约表格用的是 JS 对象记法）。
     */
    private static final List<CmsSitePublishOption> DEFAULT_OPTIONS = List.of(
            option("page.category", "1"),
            option("page.tag", "1"),
            option("page.taglist", "1"),
            option("page.archive", "0"),
            option("page.author", "1"),
            option("page.facet", "0"),
            option("page.search", "1"),
            option("page.feed", "1"),
            option("page.redirect", "1"),
            option("neighbor.limit", "1"),
            option("index.shardSize", "2000"),
            option("publish.keepReleases", "3"),
            option("publish.syncTarget", ""),
            option("publish.cron", "0 3 * * *"),
            option("url.tag", "/tag/{tagSlug}/"),
            option("url.tags", "/tags/"),
            option("url.archive", "/archive/{year}/{month}/"),
            option("url.search", "/search/"),
            option("url.thanks", "/thanks/"),
            option("url.facet", "/f/{facetPath}/"),
            option("pages.static", "[{\"code\":\"thanks\",\"url\":\"/thanks/\",\"template\":\"thanks.html\",\"type\":\"single\"}]"),
            option("facets.combos", "[]"),
            option("facets.maxPages", "500"),
            option("facets.cardinality", "50"),
            option("search.mode", "static"),
            option("search.staticMax", "50000"),
            option("search.bodyChars", "1000"),
            option("sitemap.shardSize", "10000"),
            option("feed.format", "rss"),
            option("feed.size", "20"),
            option("feed.types", "[\"article\"]"),
            option("seo.paginatedIndex", "0"),
            option("seo.facetIndex", "0"),
            option("toc.levels", "h2,h3"),
            option("reading.speed", "400"),
            option("i18n.alternates", "[]"),
            option("media.derive", "320,768,1280"),
            option("media.host", ""),
            option("url.home", "/page-{n}/"),
            option("url.list", "/{categoryPath}/page-{n}/"),
            option("page.tagMinCount", "1"),
            option("pager.labels", "首页,上一页,下一页,末页"),
            option("publish.mode", "incremental"),
            option("publish.threads", ""),
            option("publish.pageTimeout", "10"),
            option("publish.debounce", "5"),
            option("publish.rankCron", "0 * * * *"),
            option("publish.preview", "0"),
            option("publish.strict", "0"),
            option("publish.expireRedirect", "0"),
            option("comment.moderate", "1"),
            option("comment.snapshot", "1"),
            option("comment.snapshotSize", "20"),
            option("seo.noindexTypes", "[]"),
            option("feed.includeBody", "0"));

    private final CmsContentTypeMapper contentTypeMapper;
    private final CmsMenuMapper menuMapper;
    private final CmsMenuItemMapper menuItemMapper;
    private final CmsSitePublishOptionMapper publishOptionMapper;

    @Override
    @Transactional
    public void seed(Long siteId) {
        if (siteId == null) {
            // 调用方传参出错（如站点还没入库就播种）时不能静默跳过：没有日志就查不出"建了站点却没内置数据"
            log.warn("站点播种被跳过：siteId 为空");
            return;
        }
        int types = contentTypeMapper.seedBuiltinTypes(siteId, BUILTIN_TYPES);
        menuMapper.seedDefaultMenu(siteId, DEFAULT_MENU_CODE, DEFAULT_MENU_NAME);
        int options = publishOptionMapper.seedDefaults(siteId, DEFAULT_OPTIONS);
        log.info("站点 {} 播种完成：新增内置类型 {} 个、发布选项 {} 个", siteId, types, options);
    }

    /**
     * 站点删除时清场：播种进去的从属数据（内置内容类型、导航菜单与它的菜单项、发布选项）
     * 跟着站点一起逻辑删。菜单项没有 {@code site_id}，按该站点的菜单 id 连带清掉。
     */
    @Override
    @Transactional
    public void removeSeeded(Long siteId) {
        if (siteId == null) {
            log.warn("站点从属数据清理被跳过：siteId 为空");
            return;
        }
        List<Long> menuIds = menuMapper.selectList(Wrappers.<CmsMenu>lambdaQuery()
                        .select(CmsMenu::getId)
                        .eq(CmsMenu::getSiteId, siteId)).stream()
                .map(CmsMenu::getId)
                .toList();
        if (!menuIds.isEmpty()) {
            menuItemMapper.delete(Wrappers.<CmsMenuItem>lambdaQuery().in(CmsMenuItem::getMenuId, menuIds));
        }
        menuMapper.delete(Wrappers.<CmsMenu>lambdaQuery().eq(CmsMenu::getSiteId, siteId));
        contentTypeMapper.delete(Wrappers.<CmsContentType>lambdaQuery().eq(CmsContentType::getSiteId, siteId));
        publishOptionMapper.delete(Wrappers.<CmsSitePublishOption>lambdaQuery()
                .eq(CmsSitePublishOption::getSiteId, siteId));
        log.info("站点 {} 的从属数据已清理：菜单 {} 个", siteId, menuIds.size());
    }

    private static CmsContentType builtinType(String code, String name, String kind,
                                              String detailUrlPattern, String listUrlPattern, int sort) {
        CmsContentType type = new CmsContentType();
        type.setCode(code);
        type.setName(name);
        type.setKind(kind);
        type.setHierarchical(0);
        type.setDetailUrlPattern(detailUrlPattern);
        type.setListUrlPattern(listUrlPattern);
        type.setPerPage(BUILTIN_PER_PAGE);
        type.setSort(sort);
        return type;
    }

    private static CmsSitePublishOption option(String optionCode, String value) {
        CmsSitePublishOption option = new CmsSitePublishOption();
        option.setOptionCode(optionCode);
        option.setValue(value);
        return option;
    }
}
