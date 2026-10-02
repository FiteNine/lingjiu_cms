package com.lingjiuw.cms.module.cms.publish.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.entity.CmsPublishTask;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsPublishTaskMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.preview.SitePreviewServer;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import com.lingjiuw.cms.module.cms.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布对外的服务门面（static-publish.md §8）。
 *
 * <p>{@link SitePublishService} 只懂"给我站点目录与取数出口，我渲染出静态站"；本类补上
 * **另外三件事**，它们是"接口"而不是"引擎"的一部分：
 * <ol>
 *   <li>站点与站点目录的解析（库里的 {@code root_dir} → {@code cms.site.root-dir} 之下的绝对路径）；</li>
 *   <li>取数出口的构造（{@link DbContentProviderFactory} 按站点造实例）；</li>
 *   <li>预演与发布记录的查询。</li>
 * </ol>
 *
 * <p><b>为什么站点 id 不从 {@code SiteContext} 读</b>：一键全站静态化的入口既可以带站点 id
 * （后台的"发布这个站点"），也可能来自 {@code SiteContext}（右上角切换器选的站点）。两者都要能走，
 * 因此这里用 {@link SiteService#resolveSiteId(Long)} 统一收敛——它同时完成了**越权检查**
 * （只接受当前用户可访问的站点），所以控制器不需要再写一遍权限判断。
 *
 * <p>两条路的分工见 {@link #targetSiteId(Long)}：显式给了站点 id 就解析它，没给才认
 * {@code SiteContext}。**别把"没给"直接喂给 {@code resolveSiteId(null)}**——那会落到默认站点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishFacade {

    private final SitePublishService publishService;
    private final SiteService siteService;
    private final SitePreviewServer previewServer;
    private final CmsSiteMapper siteMapper;
    private final CmsPublishTaskMapper taskMapper;
    private final DbContentProviderFactory providerFactory;

    /**
     * 本次操作的目标站点。
     *
     * <p>{@code requestedSiteId} 为 null 时**必须认 {@code SiteContext}**：它是右上角切换器
     * 选中的那个站点，由 {@code SiteInterceptor} 在进控制器之前从 {@code X-Site-Id} 头解析并写入。
     * 直接调 {@code resolveSiteId(null)} 会落到"默认站点"——那正是"一键全站静态化（当前站点）"
     * 这个入口要发布错站点的成因：按钮点下去不报错，产物却落在另一个站点目录里。
     * 传 SiteContext 的值回去而不是绕开解析，是为了**把越权检查保持在同一条路上**：
     * 上下文里的站点若不在当前用户可访问清单里，仍会落到默认站点，而不是被越权访问。
     *
     * <p>{@code public} 是给需要站点 id、但不需要跑发布流程的调用方（预览站点）用的：
     * 站点解析与越权检查只留这一处。
     */
    public long targetSiteId(Long requestedSiteId) {
        return siteService.resolveSiteId(requestedSiteId != null ? requestedSiteId : SiteContext.siteId());
    }

    /** 一键全站静态化。 */
    public SitePublishService.PublishResult publish(Long requestedSiteId, PublishDtos.PublishRequest request) {
        long siteId = targetSiteId(requestedSiteId);
        return publish(siteId, siteService.siteDir(siteId),
                request == null ? null : request.modeOrDefault(),
                request == null ? "manual" : request.triggerOrDefault());
    }

    /**
     * 一键全站静态化（站点与目录都明确给出，跳过站点解析）。
     *
     * <p>两个调用方：{@link #publish(Long, PublishDtos.PublishRequest)} 自己（解析后转发），
     * 以及命令行验收入口 {@code publish/dev/SitePublishCli}——它没有 HTTP 请求，
     * 因此没有 {@code SiteContext} 可解析。
     */
    public SitePublishService.PublishResult publish(long siteId, Path siteDir,
                                                    SitePublishService.Mode mode, String trigger) {
        ContentProvider provider = providerFactory.forSite(siteId);
        return publishService.publish(siteId, siteDir, provider,
                mode == null ? SitePublishService.Mode.full : mode,
                trigger == null || trigger.isBlank() ? "manual" : trigger, true);
    }

    /**
     * 预演：只算计划，不写盘、不写库（§8.1："②③④ 分离是『可预演』的前提"）。
     *
     * <p>同时做发布前的模板体检（主题目录、首页/列表兜底模板在不在），把"模板写错了"挡在
     * 渲染之前——这是"模板写错 → 全站坏页"最便宜的一道防线。
     */
    public PublishDtos.PublishPreview preview(Long requestedSiteId) {
        long siteId = targetSiteId(requestedSiteId);
        return preview(siteId, siteService.siteDir(siteId));
    }

    /** 预演（站点与目录都明确给出）。 */
    public PublishDtos.PublishPreview preview(long siteId, Path siteDir) {
        ContentProvider provider = providerFactory.forSite(siteId);
        List<String> problems = publishService.preflight(siteId, siteDir, provider);
        String theme = provider.site().theme() == null || provider.site().theme().isBlank()
                ? "_default" : provider.site().theme();
        if (!problems.isEmpty()) {
            return new PublishDtos.PublishPreview(siteId, theme, 0, List.of(), List.of(), problems);
        }
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(
                new FileTemplateSource(themeRoot(siteDir, theme)), theme);
        List<String> warnings = new ArrayList<>();
        List<PublishDtos.PublishedPage> pages = new ArrayList<>();
        SitePlanner.Result planned;
        try {
            planned = new SitePlanner(provider, lookup).plan();
        } catch (RuntimeException e) {
            // 静默变成一条提示 = "预演页面集合与实际发布不一致"且没人知道（发布约定第 2 条）
            log.error("预演失败 siteId={}", siteId, e);
            problems.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return new PublishDtos.PublishPreview(siteId, theme, 0, List.of(), List.of(), problems);
        }
        warnings.addAll(planned.warnings());
        for (Plan plan : planned.pages()) {
            pages.add(PublishDtos.describe(plan));
        }
        return new PublishDtos.PublishPreview(siteId, theme, pages.size(), pages, warnings, problems);
    }

    /**
     * 主题根目录：{@code <站点目录>/template/<theme>}。
     *
     * <p>{@code theme} 来自站点配置（{@code cms_site.theme} / 站点选项），可能含 {@code ..} 或
     * 是绝对路径——直接 {@code resolve} 会拼出站点目录之外的路径。统一走
     * {@link SitePathBoundary#resolveUnder} 把路径边界收在一处（§7.8、§11.4）。
     */
    private static Path themeRoot(Path siteDir, String theme) {
        return SitePathBoundary.resolveUnder(siteDir.resolve("template"), theme);
    }

    /** 最近的发布批次记录（后台"发布记录"页用；§8.3）。 */
    public List<CmsPublishTask> recentTasks(Long requestedSiteId, int limit) {
        long siteId = targetSiteId(requestedSiteId);
        return taskMapper.selectList(Wrappers.<CmsPublishTask>lambdaQuery()
                .eq(CmsPublishTask::getSiteId, siteId)
                .orderByDesc(CmsPublishTask::getId)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    /**
     * 预览站点：给当前站点**已经落盘的产物**一个只读地址。
     *
     * <p>它不发布、不写盘，但**不是纯读**：第一次调用会为该站点起一个预览服务（见
     * {@link SitePreviewServer}），之后重复调用只回同一个地址。
     */
    public PublishDtos.PreviewSite previewSite(Long requestedSiteId) {
        long siteId = targetSiteId(requestedSiteId);
        CmsSite site = requireSite(siteId);
        return new PublishDtos.PreviewSite(siteId, site.getName(),
                siteService.siteDir(siteId).resolve("www").toString(), previewServer.url(siteId));
    }

    /**
     * 当前请求站点下的站点实体；不存在即报错（控制器用它做一次"站点还在不在"的兜底）。
     *
     * <p>站点 id 先经 {@link #targetSiteId(Long)} 收敛：本类其它方法都不绕过
     * {@code SiteService.resolveSiteId} 的"当前用户可访问站点"校验，这里以前直接
     * {@code selectById}，等于留了一条读未授权站点实体的路（名称、rootDir 等）。
     */
    public CmsSite requireSite(Long requestedSiteId) {
        long siteId = targetSiteId(requestedSiteId);
        CmsSite site = siteMapper.selectById(siteId);
        if (site == null) {
            throw new BizException("站点不存在或已被删除");
        }
        return site;
    }
}
