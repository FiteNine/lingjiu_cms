package com.lingjiuw.cms.module.cms.publish.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.entity.CmsPublishTask;
import com.lingjiuw.cms.module.cms.publish.service.PublishDtos;
import com.lingjiuw.cms.module.cms.publish.service.PublishFacade;
import com.lingjiuw.cms.module.cms.publish.service.SitePublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 静态化发布接口（static-publish.md §8.1、§8.2）。
 *
 * <p>入口覆盖"发布 / 预演 / 预览 / 看记录"四件事：
 * <ul>
 *   <li>{@code POST /api/cms/publish/site} —— **一键全站静态化**。默认 full：把模板 + 内容
 *       渲染成一套完整的静态站，落到 {@code sites/<站点>/www/}，并重建
 *       sitemap / feed / robots / 搜索索引 / {@code cms-site.json}；</li>
 *   <li>{@code POST /api/cms/publish/site/preview} —— 预演：只算页面计划，不写盘。给出
 *       "本次将出哪些页、用哪个模板"，以及主题体检的问题清单；</li>
 *   <li>{@code GET /api/cms/publish/site/preview-url} —— **预览站点**：把已发布的
 *       {@code www/} 用第二个端口原样提供出来（后台「预览站点」按钮开它）；</li>
 *   <li>{@code POST /api/cms/publish/sites/{siteId}} —— 指定站点的同一次动作（后台多站点用）；</li>
 *   <li>{@code GET /api/cms/publish/sites/{siteId}/tasks} —— 最近的发布批次记录。</li>
 * </ul>
 *
 * <p>权限沿用既有清单里的 {@code cms:publish:run}（{@code V20261002090010__cms_publish_menu.sql}
 * 已经建了这一行并授给了 admin 角色），因此**本次不新增权限位，也不新增迁移**。
 * "发布全站"与"发布一条文章"（{@code cms:article:publish}）刻意分开：前者改的是全站产物，
 * 后者改的是一条内容的状态。
 */
@RestController
@RequestMapping("/api/cms/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishFacade facade;

    /** 一键全站静态化（当前站点；右上角切换器选的那个）。 */
    @PostMapping("/site")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    @OperLog(module = "站点发布", action = "一键全站静态化")
    public Result<SitePublishService.PublishResult> publishCurrent(
            @RequestBody(required = false) PublishDtos.PublishRequest request) {
        return Result.ok(facade.publish(null, request));
    }

    /** 一键全站静态化（指定站点）。 */
    @PostMapping("/sites/{siteId}")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    @OperLog(module = "站点发布", action = "一键全站静态化")
    public Result<SitePublishService.PublishResult> publish(@PathVariable Long siteId,
                                                            @RequestBody(required = false)
                                                            PublishDtos.PublishRequest request) {
        return Result.ok(facade.publish(siteId, request));
    }

    /** 预演：本次会出哪些页面（不写盘）。 */
    @GetMapping("/site/preview")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    public Result<PublishDtos.PublishPreview> previewCurrent() {
        return Result.ok(facade.preview(null));
    }

    /** 预演（指定站点）。 */
    @GetMapping("/sites/{siteId}/preview")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    public Result<PublishDtos.PublishPreview> preview(@PathVariable Long siteId) {
        return Result.ok(facade.preview(siteId));
    }

    /** 最近的发布批次记录（默认 20 条）。 */
    @GetMapping("/sites/{siteId}/tasks")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    public Result<List<CmsPublishTask>> tasks(@PathVariable Long siteId,
                                              @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(facade.recentTasks(siteId, limit));
    }

    /** 当前站点的发布批次记录。 */
    @GetMapping("/site/tasks")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    public Result<List<CmsPublishTask>> currentTasks(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(facade.recentTasks(null, limit));
    }

    /**
     * 预览站点：给当前站点**已发布的产物**一个只读地址（不发布、不写盘，需要时现场起服务）。
     *
     * <p>与 {@link #previewCurrent()} 的区别就是「预演」与「预览」这两个中文词的区别：
     * 预演算的是"这次会出哪些页面"（还没有产物），预览看的是"上次发布出来的那个站"（已经有产物）。
     * 因此它既不写库也不进操作日志，重复点按钮拿到同一个地址。
     */
    @GetMapping("/site/preview-url")
    @PreAuthorize("hasAuthority('cms:publish:run')")
    public Result<PublishDtos.PreviewSite> previewUrl() {
        return Result.ok(facade.previewSite(null));
    }
}
