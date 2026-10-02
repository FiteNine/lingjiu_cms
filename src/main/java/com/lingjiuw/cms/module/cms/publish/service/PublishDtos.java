package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.UrlPatternResolver;

import java.util.List;

/**
 * 发布接口的请求 / 响应载体（static-publish.md §8.2 的"预演"、§7.2.2 的产物清单）。
 *
 * <p>放在 service 包而不是 {@code module/cms/dto}：这三个类型是**发布引擎的公开契约**，
 * 与引擎放在一起，读代码时"接口长什么样"与"引擎怎么实现"只有一次跳转。
 */
public final class PublishDtos {

    private PublishDtos() {
    }

    /**
     * 一键全站静态化的请求。
     *
     * @param mode    {@code full}（默认，全量重写）/ {@code incremental}（只重写内容变了的页）
     * @param trigger 触发来源，写进发布批次记录（默认 {@code manual}）
     */
    public record PublishRequest(String mode, String trigger) {

        public SitePublishService.Mode modeOrDefault() {
            // 先把首尾空白去掉：带空格的 " incremental " 以前会静默落进 full（全量重写，
            // 写盘开销大得多），调用方与前端都无从察觉。
            String normalized = mode == null ? "" : mode.trim();
            return "incremental".equalsIgnoreCase(normalized)
                    ? SitePublishService.Mode.incremental : SitePublishService.Mode.full;
        }

        public String triggerOrDefault() {
            return trigger == null || trigger.isBlank() ? "manual" : trigger.trim();
        }
    }

    /** 发布结果的一行（页面级颗粒度，便于后台与排障核对"到底出了哪些页"）。 */
    public record PublishedPage(String url, String path, String pageType, String template) {
    }

    /**
     * 预演结果（§8.2："只算计划"）。
     *
     * <p>它回答"这次会出哪些页面、用哪个模板、有没有 URL 冲突"，**不写盘、不写库**。
     * 后台应当在用户按"立即发布"之前拿它给一个"本次将重写 42 个页面"的预览。
     */
    public record PublishPreview(long siteId, String theme, int totalPages, List<PublishedPage> pages,
                                List<String> warnings, List<String> problems) {
    }

    /**
     * 预览站点：已发布产物的只读出口（后台「预览站点」按钮开这个地址）。
     *
     * @param url       预览地址（独占一个端口，站点在那里占着 {@code /}）
     * @param outputDir 被预览的产物目录，与发布结果里的 {@code outputDir} 是同一个
     */
    public record PreviewSite(long siteId, String siteName, String outputDir, String url) {
    }

    /** 把一个计划项转成响应行（URL / 产物路径 / 页面类型 / 模板四件套）。 */
    public static PublishedPage describe(Plan plan) {
        return new PublishedPage(plan.url(), plan.path(), pageTypeName(plan.pageType()),
                plan.template());
    }

    /**
     * 页面类型对外的稳定名字。
     *
     * <p>与 {@code PageType.names()} / §6.7 矩阵的列名一致：{@code PAGE404 → "404"}、
     * {@code FEED → "feed"}。预演结果与 {@code manifest.json} 的 {@code pageType} 必须共用这一处，
     * 否则同一个页面在两条路上会给出两个不同的字符串（预演 "404"、清单 "PAGE404"）。
     */
    public static String pageTypeName(PageType pageType) {
        return switch (pageType) {
            case PAGE404 -> "404";
            case FEED -> "feed";
            default -> pageType.name();
        };
    }

    /** 从 URL 反推产物路径（后台给用户看"这个页面对应哪个文件"时用）。 */
    public static String artifactPathOf(String url) {
        return UrlPatternResolver.toArtifactPath(url);
    }
}
