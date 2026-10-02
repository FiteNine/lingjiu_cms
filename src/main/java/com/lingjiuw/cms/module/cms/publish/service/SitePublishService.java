package com.lingjiuw.cms.module.cms.publish.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.entity.CmsPublishTask;
import com.lingjiuw.cms.module.cms.mapper.CmsPublishTaskMapper;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全站静态化引擎（static-publish.md §8.1 的九个阶段）。
 *
 * <p>一次发布 = 一个批次，作用于**一个站点**。站点级串行由调用方保证（同一站点同一时刻只有
 * 一个批次），不同站点之间天然独立——因此本类**无跨请求状态**，全部状态在方法内。
 *
 * <p>九个阶段与实现落点的对应：
 * <table>
 *   <caption>阶段对照</caption>
 *   <tr><td>① 冻结</td><td>读一次 {@code ContentProvider}（站点配置 / 类型定义 / 模板指纹）</td></tr>
 *   <tr><td>② 计划</td><td>{@link SitePlanner#plan()}</td></tr>
 *   <tr><td>③ 差异</td><td>{@code manifest.json} 对比（本类）</td></tr>
 *   <tr><td>④ 渲染</td><td>{@link PageRenderer#render}</td></tr>
 *   <tr><td>⑤ 写盘</td><td>{@link ArtifactWriter#write}</td></tr>
 *   <tr><td>⑥ 聚合产物</td><td>{@link SiteOutputs}</td></tr>
 *   <tr><td>⑦ 校验</td><td>空页与 sitemap/noindex 一致性检查（本类）</td></tr>
 *   <tr><td>⑧ 批次落定</td><td>{@code .publish/manifest.json} 原子替换</td></tr>
 *   <tr><td>⑨ 搬运</td><td>{@code publish.syncTarget} 为空时跳过（§8.10）</td></tr>
 * </table>
 *
 * <p><b>与契约的一处如实偏离</b>：MODE=full 按 §8.4 的定义是"全量计划 + 差异渲染"，
 * 而本类在 full 模式下**无条件重写全部页面**。理由：full 就是"一键全站静态化"这个动作本身，
 * 用户按它的预期是"把所有页面重出一遍"；"全量计划 + 差异渲染"每天由定时任务（§8.8）负责。
 * 差异渲染的能力仍然保留在 incremental 模式下，两者不是一回事。
 */
@Slf4j
@Service
public class SitePublishService {

    /** 发布模式（{@code cms_publish_task.mode}）。 */
    public enum Mode {
        /** 全量：重写全部页面（一键全站静态化）。 */
        full,
        /** 增量：只重写内容真的变了的页面（hash 二次判断）。 */
        incremental
    }

    /** 一次批次的结果（后台展示与 API 响应都用它）。 */
    public record PublishResult(String batchId, long siteId, String mode, String theme,
                                int totalPages, int writtenPages, int skippedPages, int failedPages,
                                int deletedArtifacts, List<String> aggregateArtifacts,
                                List<String> warnings, List<String> errors, long elapsedMillis,
                                String siteDir, String outputDir) {

        public boolean success() {
            return errors.isEmpty() && failedPages == 0;
        }
    }

    /** manifest 里的一页（§8.4 的依赖清单的落地形态）。 */
    private record ManifestEntry(String path, String pageType, String url, String hash,
                                 long size, long writtenAt, String template) {
    }

    private final TagRegistry registry;
    private final List<TemplateValidator> validators;
    private final CmsPublishTaskMapper taskMapper;

    /**
     * 站点级串行（§8.1："同一站点同一时刻只允许一个批次（产物 + GC 会打架）"）。
     *
     * <p>这里是**进程内**的锁，正确性边界如实说明：单实例部署够用；多实例部署要靠
     * {@code cms_publish_lock} 表（迁移已建好，但"抢锁/放锁"属于 §8.8 的定时任务层，
     * 本期的手动一键发布不做跨实例互斥）。同一站点并发触发时，后到的请求在这里排队而不是
     * 与先到的打架——排队比"拒绝"好：用户的期望是"我点了就发布"，不是"我点了被告知稍后再试"。
     */
    private static final ConcurrentHashMap<Long, java.util.concurrent.locks.ReentrantLock> SITE_LOCKS =
            new ConcurrentHashMap<>();

    public SitePublishService(TagRegistry registry, List<TemplateValidator> validators,
                              CmsPublishTaskMapper taskMapper) {
        this.registry = registry;
        this.validators = validators == null ? List.of() : List.copyOf(validators);
        this.taskMapper = taskMapper;
    }

    /**
     * **一键全站静态化**：把该站点的模板 + 内容渲染成一套可用的静态站点。
     *
     * @param siteId       站点 id
     * @param siteDir      站点目录（{@code sites/<站点>}，含 {@code template/} {@code data/} {@code www/}）
     * @param provider     该站点的取数出口
     * @param mode         发布模式
     * @param trigger      触发来源，写进 {@code cms_publish_task.trigger}（manual / content / schedule…）
     * @param recordTask   是否写发布批次记录（测试时可以关掉，免得依赖数据库）
     */
    public PublishResult publish(long siteId, Path siteDir, ContentProvider provider, Mode mode,
                                 String trigger, boolean recordTask) {
        java.util.concurrent.locks.ReentrantLock lock =
                SITE_LOCKS.computeIfAbsent(siteId, key -> new java.util.concurrent.locks.ReentrantLock());
        lock.lock();
        try {
            return doPublish(siteId, siteDir, provider, mode, trigger, recordTask);
        } finally {
            lock.unlock();
        }
    }

    private PublishResult doPublish(long siteId, Path siteDir, ContentProvider provider, Mode mode,
                                    String trigger, boolean recordTask) {
        long started = System.currentTimeMillis();
        SiteConfig site = provider.site();
        String batchId = batchIdOf(siteId);
        Path www = siteDir.resolve("www");

        // warnings 会被**线程池里的 renderOne 并发 add**，因此必须用同步容器
        // （同批次的 manifest / writtenPaths / pageErrors 都已经是并发保护的，这里以前漏了）。
        List<String> warnings = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> errors = new ArrayList<>();

        // ① 冻结：主题目录与模板读取出口
        String theme = themeOf(site);
        // theme 来自站点配置，直接 resolve 会拼出站点目录之外的路径（§7.8 的路径边界）
        Path themeRoot = SitePathBoundary.resolveUnder(siteDir.resolve("template"), theme);
        if (!Files.isDirectory(themeRoot)) {
            throw new BizException("主题目录不存在：" + themeRoot
                    + "（站点选项 theme / cms_site.theme 指向它；§7.3 的目录约定见文档）");
        }
        FileTemplateSource source = new FileTemplateSource(themeRoot);
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(source, theme);
        TemplateCompiler compiler = new TemplateCompiler(source, registry, validators);
        TemplateRenderer renderer = new DefaultTemplateRenderer(registry);
        PageRenderer pages = new PageRenderer(compiler, renderer, provider, lookup);
        ArtifactWriter writer = new ArtifactWriter(www);
        writer.prepare();

        // ② 计划（纯读；冲突检测在这里抛 E4004）
        SitePlanner planner = new SitePlanner(provider, lookup);
        SitePlanner.Result planned = planner.plan();
        warnings.addAll(planned.warnings());

        // ③ 差异：读上一批的 manifest
        Path publishDir = siteDir.resolve(".publish");
        Map<String, ManifestEntry> previous = readManifest(publishDir.resolve("manifest.json"));
        Map<String, String> previousArtifacts = readArtifactIndex(previous);

        // ④ 渲染 + ⑤ 写盘
        Map<String, ManifestEntry> manifest = new ConcurrentHashMap<>();
        List<SiteOutputs.RenderedPage> rendered = java.util.Collections.synchronizedList(new ArrayList<>());
        // 本批次**真的写到盘上**的全部产物路径（含派生页）。GC 必须按它判"在不在计划里"，
        // 不能按 planned.pages()——那里面只有各分页的第 1 页，用它做 plan 会把 page-2/… 当成幽灵页删掉。
        java.util.Set<String> writtenPaths = java.util.concurrent.ConcurrentHashMap.newKeySet();
        AtomicInteger written = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> pageErrors = java.util.Collections.synchronizedList(new ArrayList<>());

        int threads = Math.max(1, threads(provider));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Plan plan : planned.pages()) {
                futures.add(pool.submit(() -> renderOne(plan, mode, pages, writer, previous,
                        manifest, rendered, writtenPaths, written, skipped, failed, pageErrors,
                        warnings)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("发布被中断：" + e.getMessage());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    errors.add(cause == null ? e.toString() : cause.getMessage());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        errors.addAll(pageErrors);

        // ⑥ 聚合产物。**每个产物各自兜错**：feed 的日期格式一旦写错，不应该把 robots.txt、
        // 搜索索引、cms-site.json 一起带走（它们互相没有依赖，只是写在同一段里）。
        List<String> aggregate = new ArrayList<>();
        SiteOutputs outputs = new SiteOutputs(provider, writer);
        // 内容水位要在所有聚合产物之前定下来：sitemap / feed / 索引 / cms-site.json 都写它
        outputs.watermarkFromData();
        // 幂等（§8.4）：sitemap 的条目顺序必须由 URL 决定，不能由并发渲染的完成顺序决定
        List<SiteOutputs.RenderedPage> ordered = new ArrayList<>(rendered);
        ordered.sort(Comparator.comparing(page -> page.plan().url()));
        step("sitemap", errors, () -> aggregate.addAll(outputs.writeSitemap(ordered)));
        step("feed", errors, () -> aggregate.addAll(outputs.writeFeed()));
        step("robots.txt", errors, () -> outputs.writeRobots(robotsTemplate(source)));
        step("redirects.conf", errors, outputs::writeRedirects);
        step("搜索索引", errors, () -> aggregate.addAll(outputs.writeSearchIndex()));
        step("主题资源", errors, () -> writer.copyAssets(themeRoot.resolve("assets")));
        // 主题 static/ 落到 www/ 根（favicon.ico 这类"必须占根上某个固定路径"的文件），
        // 并登记进 writtenPaths：它们是本批次写出的产物，否则会被下一次 GC 当成多余文件删掉
        step("主题静态文件", errors, () -> {
            List<String> copied = writer.copyStatic(themeRoot.resolve("static"));
            writtenPaths.addAll(copied);
            aggregate.addAll(copied);
        });
        // cms-site.json **最后写**：它出现代表本批次的产物已完整（§8.5）
        step("cms-site.json", errors, () -> {
            outputs.writeSiteMarker();
            aggregate.add("cms-site.json");
        });

        // ⑦ 校验：空页与 sitemap / noindex 一致性（默认只警告；publish.strict=1 时失败）
        validate(rendered, warnings, errors, site);

        // ⑧ 批次落定：manifest 原子替换
        List<ManifestEntry> entries = new ArrayList<>(manifest.values());
        entries.sort(Comparator.comparing(ManifestEntry::path));
        if (!writeManifest(publishDir.resolve("manifest.json"), entries)) {
            // 清单写不出去 = 下一批的差异判断与 GC 都在用陈旧数据，调用方必须看得见（§8.5）
            errors.add("批次清单 manifest.json 写入失败：" + publishDir.resolve("manifest.json")
                    + "（下一批的差异判断与 GC 会用到陈旧清单，请检查目录权限）");
        }

        // ⑨ 产物 GC（§8.6）：只认**本批次真的写出过的路径**，且只在 full 模式下删
        // （§8.3 的 v2.2 死限：narrow / aggregate 一律跳过 GC）
        int deleted = mode == Mode.full
                ? collectGarbage(writer, writtenPaths, previousArtifacts, warnings)
                : 0;

        long elapsed = System.currentTimeMillis() - started;
        PublishResult result = new PublishResult(batchId, siteId, mode.name(), theme,
                planned.pages().size(), written.get(), skipped.get(), failed.get(), deleted,
                List.copyOf(aggregate), List.copyOf(warnings), List.copyOf(errors), elapsed,
                siteDir.toString(), www.toString());

        if (recordTask) {
            recordTask(siteId, batchId, mode, trigger, result);
        }
        log.info("全站静态化完成：站点 {} 批次 {} 计划 {} 页，写出 {} 页，跳过 {} 页，失败 {} 页，"
                        + "删除 {} 个产物，用时 {} ms", siteId, batchId, result.totalPages(),
                result.writtenPages(), result.skippedPages(), result.failedPages(), deleted, elapsed);
        return result;
    }

    /* ---------------- 单页渲染 ---------------- */

    private void renderOne(Plan plan, Mode mode, PageRenderer pages, ArtifactWriter writer,
                           Map<String, ManifestEntry> previous,
                           Map<String, ManifestEntry> manifest,
                           List<SiteOutputs.RenderedPage> rendered, java.util.Set<String> writtenPaths,
                           AtomicInteger written, AtomicInteger skipped, AtomicInteger failed,
                           List<String> pageErrors, List<String> warnings) {
        try {
            // 第 1 页：主路径形态
            PageRenderer.Rendered first = pages.render(plan, 1);
            String html = first.html();
            if (writePage(writer, plan, html, mode, previous, manifest)) {
                skipped.incrementAndGet();
            } else {
                written.incrementAndGet();
            }
            writtenPaths.add(plan.path());
            rendered.add(new SiteOutputs.RenderedPage(plan, first.pageScope(), html, 0L));

            // 派生页（§5.4 第 5 步）：同一个模板、同一份上下文，只覆盖 page.pageNo。
            // 总页数刚由第 1 页的分页主体算出来，所以计划是"渲染第 1 页再问总页数"。
            // 差异判定与第 1 页同口径：incremental 下派生页也要能被跳过（否则分页页永远全量重写）。
            boolean paginates = plan.pageUrls() != null && first.totalPages() > 1;
            if (paginates && pages.paginates(plan)) {
                for (int pageNo = 2; pageNo <= first.totalPages(); pageNo++) {
                    Plan derived = derived(plan, pageNo);
                    PageRenderer.Rendered next = pages.render(derived, pageNo);
                    if (writePage(writer, derived, next.html(), mode, previous, manifest)) {
                        skipped.incrementAndGet();
                    } else {
                        written.incrementAndGet();
                    }
                    writtenPaths.add(derived.path());
                    rendered.add(new SiteOutputs.RenderedPage(derived, next.pageScope(),
                            next.html(), 0L));
                }
            }

            // 正文分页的 W5002（分页符落在块级元素内部）随页收集（§5.2.3）
            collectContentPaginationWarnings(first, warnings);
        } catch (PublishException e) {
            failed.incrementAndGet();
            pageErrors.add(plan.url() + "：" + e.getMessage());
        } catch (BizException e) {
            failed.incrementAndGet();
            pageErrors.add(plan.url() + "：" + e.getMessage());
        } catch (RuntimeException e) {
            failed.incrementAndGet();
            pageErrors.add(plan.url() + "：" + describe(e));
            log.warn("渲染页面失败：{}", plan.url(), e);
        }
    }

    /**
     * 写一页产物，并按增量语义决定要不要跳。
     *
     * <p>跳过条件是三条同时成立（§8.4 的 hash 二次判断）：模式是 {@code incremental}、
     * 上一批清单里这一页的内容哈希与本批一致、且产物文件确实还在盘上（被人工删掉的要重写）。
     * 第 1 页与派生页（{@code page-2/…}）走**同一个**判定——派生页以前是无条件重写的。
     *
     * @return true = 跳过了写入
     */
    private static boolean writePage(ArtifactWriter writer, Plan plan, String html, Mode mode,
                                     Map<String, ManifestEntry> previous,
                                     Map<String, ManifestEntry> manifest) {
        String hash = sha256(html);
        ManifestEntry old = previous.get(plan.path());
        long size = html.getBytes(StandardCharsets.UTF_8).length;
        if (mode == Mode.incremental && old != null && hash.equals(old.hash())
                && Files.exists(writer.resolve(plan.path()))) {
            manifest.put(plan.path(), new ManifestEntry(plan.path(),
                    PublishDtos.pageTypeName(plan.pageType()), plan.url(), hash, size,
                    old.writtenAt(), plan.template()));
            return true;
        }
        writer.write(plan.path(), html);
        manifest.put(plan.path(), new ManifestEntry(plan.path(),
                PublishDtos.pageTypeName(plan.pageType()), plan.url(), hash, size,
                System.currentTimeMillis(), plan.template()));
        return false;
    }

    /**
     * 一条能定位到代码行的失败描述。
     *
     * <p>只写 {@code e.toString()} 会得到一个"某 Bean 创建失败"的结论，而真正抛异常的标签类
     * 藏在栈里——排查一次要在两个进程之间来回猜。这里把**第一条落在本项目包里的栈帧**贴出来，
     * 报错即诊断（§10.3）在页面级的落地。
     */
    private static String describe(RuntimeException e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            if (frame.getClassName().startsWith("com.lingjiuw.cms")) {
                return e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "（首个本工程栈帧：" + frame + "）";
            }
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 第 2..N 页：同一模板、同一上下文，只有 URL / 产物路径 / 页号不同。
     *
     * <p>页面类型改成 {@link PageType#DPAGE}——**这一步决定它进不进 sitemap**：
     * {@code DPAGE} 不在 {@code SiteOutputs.isIndexable} 的清单里，因此派生页不会被收录
     * （§7.6：只收录 S 类页面，分页的第 2..N 页不是独立页面）。
     */
    private Plan derived(Plan plan, int pageNo) {
        String url = plan.pageUrls().pageUrl(pageNo);
        return new Plan(PageType.DPAGE, url,
                com.lingjiuw.cms.module.cms.publish.model.UrlPatternResolver.toArtifactPath(url),
                plan.template(), plan.typeCode(), plan.source(), pageNo, plan.pageUrls(),
                plan.entry(), plan.listQuery(), plan.scope(), plan.title(), plan.pageType());
    }

    /**
     * 跑一个聚合产物步骤，失败只记一笔（不牵连同一批里的其他产物）。
     *
     * <p>为什么必须逐个兜错：第 ⑥ 阶段有七种产物，它们之间**没有依赖**。整段一个 {@code try}
     * 时，feed 的日期格式写错会让 robots.txt / 搜索索引 / {@code cms-site.json} 全部不产出——
     * 而 {@code cms-site.json} 正是"本批次产物已完整"的标记，它缺失会让运维误判整次发布失败。
     */
    private static void step(String what, List<String> errors, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            errors.add("聚合产物 " + what + " 失败：" + e.getMessage());
        }
    }

    private void collectContentPaginationWarnings(PageRenderer.Rendered rendered, List<String> warnings) {
        // W5002 由 DetailTag 在预解析时记进 ValidationReport；编译器的 report 没有外露到这一层，
        // 因此这里只在 noindex 一致性上补一条（正文分页符落在块内属于入库期问题，期 5 补）
        Object kind = rendered.pageScope().get("paginationKind");
        if (kind != null && "content".equals(String.valueOf(kind))) {
            int totalPages = rendered.totalPages();
            if (totalPages > 1) {
                warnings.add("正文分页产生 " + totalPages + " 页（paginationKind=content）");
            }
        }
    }

    /* ---------------- ⑦ 校验（§8.1 第 ⑦ 阶段） ---------------- */

    private void validate(List<SiteOutputs.RenderedPage> rendered, List<String> warnings,
                          List<String> errors, SiteConfig site) {
        boolean strict = site.flag("publish.strict", false);
        for (SiteOutputs.RenderedPage page : rendered) {
            if (page.html() == null || page.html().isBlank()) {
                String message = "空产物：" + page.plan().url() + "（模板渲染出了 0 字节）";
                warnings.add(message);
                if (strict) {
                    errors.add(message);
                }
            }
            // sitemap 与 noindex 必须一致（§7.6）：两处都读同一个 page.noindex，这里只核对二者同源
            if (page.plan().pageType() == PageType.PAGE404 && !page.noindex()) {
                String message = "404 页被标成了可收录：" + page.plan().url();
                warnings.add(message);
                if (strict) {
                    errors.add(message);
                }
            }
        }
    }

    /* ---------------- ⑨ GC（§8.6） ---------------- */

    /**
     * 产物 GC（§8.6）。
     *
     * <p>输入是**本批次真的写出过的路径集合**：{@code writtenPaths} 由渲染阶段填，
     * 它含派生页（{@code page-2/…}）。用 {@code planned.pages()} 会漏掉派生页，于是每次重发
     * 都会把分页页当幽灵页删掉——这是"第 2 页在，第 3 页不见了"这类线上事故的成因。
     *
     * <p>判定表（§8.6）：∈ 本次写出 → 保留；∉ 本次写出 且 ∈ 上一批清单 → 删；
     * ∉ 本次写出 且 ∉ 上一批清单 → **保留**（不是本引擎产出的东西不碰）。
     */
    private int collectGarbage(ArtifactWriter writer, java.util.Set<String> writtenPaths,
                               Map<String, String> previousArtifacts, List<String> warnings) {
        java.util.Set<String> planned = new java.util.HashSet<>(writtenPaths);
        // 引擎固定的非页面产物不进计划，但必须保住（它们由聚合阶段重写）
        planned.add("sitemap.xml");
        planned.add("feed.xml");
        planned.add("robots.txt");
        planned.add("redirects.conf");
        planned.add("cms-site.json");
        planned.add("search/index.json");
        int deleted = 0;
        for (String artifact : writer.listArtifacts()) {
            if (planned.contains(artifact)) {
                continue;
            }
            if (artifact.startsWith("assets/") || artifact.startsWith("search/shard-")
                    || isSitemapShard(artifact)) {
                continue;
            }
            // 只删"历史上由引擎写出过"的产物；不是本引擎产出的东西不碰（§8.6 第 3 行）
            if (!previousArtifacts.containsKey(artifact)) {
                continue;
            }
            if (writer.delete(artifact)) {
                deleted++;
                warnings.add("删除幽灵产物：" + artifact + "（W5004：若它被人工改过，这次改动已丢失）");
            }
            previousArtifacts.remove(artifact);
        }
        // 上一批清单里有、但磁盘上已经不在的条目不用再删（file 索引以磁盘为准）
        writer.pruneEmptyDirectories();
        return deleted;
    }

    private static boolean isSitemapShard(String artifact) {
        return artifact.startsWith("sitemap-") && artifact.endsWith(".xml");
    }

    /* ---------------- manifest（§8.4 的落地） ---------------- */

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, ManifestEntry> readManifest(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            Object pages = root.get("pages");
            if (!(pages instanceof List<?> list)) {
                return Map.of();
            }
            Map<String, ManifestEntry> entries = new LinkedHashMap<>();
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> entry = (Map<String, Object>) map;
                String path = entry.get("path") == null ? null : String.valueOf(entry.get("path"));
                if (path == null) {
                    continue;
                }
                entries.put(path, new ManifestEntry(path, text(entry.get("pageType")),
                        text(entry.get("url")), text(entry.get("hash")), number(entry.get("size")),
                        number(entry.get("writtenAt")), text(entry.get("template"))));
            }
            return entries;
        } catch (Exception e) {
            log.warn("读 manifest 失败（按空处理，本次会全量重写）：{}（{}）", file, e.getMessage());
            return Map.of();
        }
    }

    /** 上一批次的产物索引：path → 内容哈希（GC 的"known"集合）。 */
    private Map<String, String> readArtifactIndex(Map<String, ManifestEntry> manifest) {
        Map<String, String> index = new LinkedHashMap<>();
        for (ManifestEntry entry : manifest.values()) {
            index.put(entry.path(), entry.hash() == null ? "" : entry.hash());
        }
        return index;
    }

    /**
     * 原子替换批次清单（§8.5：先写 {@code .tmp} 再整体 rename）。
     *
     * @return 写成功返回 true；失败时调用方要把它记进批次错误——清单是下一批差异判断与 GC 的
     *         known 集合，静默失败会留下陈旧清单，导致下一次误删或漏删产物
     */
    private boolean writeManifest(Path file, List<ManifestEntry> entries) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("engineVersion", SiteOutputs.ENGINE_VERSION);
        root.put("generatedAt", LocalDateTime.now().toString());
        List<Map<String, Object>> pages = new ArrayList<>(entries.size());
        for (ManifestEntry entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", entry.path());
            row.put("pageType", entry.pageType());
            row.put("url", entry.url());
            row.put("hash", entry.hash());
            row.put("size", entry.size());
            row.put("writtenAt", entry.writtenAt());
            row.put("template", entry.template());
            pages.add(row);
        }
        root.put("pages", pages);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("manifest.json.tmp");
            Files.writeString(temp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.warn("写 manifest 失败：{}（{}）", file, e.getMessage());
            return false;
        }
    }

    /* ---------------- 发布批次记录（§8.3） ---------------- */

    private void recordTask(long siteId, String batchId, Mode mode, String trigger,
                            PublishResult result) {
        try {
            CmsPublishTask task = new CmsPublishTask();
            task.setSiteId(siteId);
            task.setBatchId(batchId);
            task.setTrigger(trigger == null || trigger.isBlank() ? "manual" : trigger);
            task.setMode(mode.name());
            task.setStatus(result.success() ? "SUCCESS" : "PARTIAL");
            task.setTotal(result.totalPages());
            task.setDone(result.writtenPages() + result.skippedPages());
            task.setFailed(result.failedPages());
            String message = result.errors().isEmpty() ? null
                    : String.join("\n", result.errors().subList(0, Math.min(20, result.errors().size())));
            task.setMessage(message);
            task.setStartTime(LocalDateTime.now().minus(Duration.ofMillis(result.elapsedMillis())));
            task.setEndTime(LocalDateTime.now());
            taskMapper.insert(task);
        } catch (RuntimeException e) {
            // 批次记录失败不该让一次已经写完盘的发布变成失败
            log.warn("写发布批次记录失败：{}", e.getMessage());
        }
    }

    /* ---------------- 助手 ---------------- */

    private static String themeOf(SiteConfig site) {
        String theme = site.theme();
        return theme == null || theme.isBlank() ? "_default" : theme;
    }

    private int threads(ContentProvider provider) {
        int configured = provider.site().number("publish.threads", 0);
        return configured > 0 ? configured : Math.min(4, Runtime.getRuntime().availableProcessors());
    }

    /** 主题里的 {@code robots.txt} 模板：只展开 {@code [field:…/]}（它没有 {@code {cms:}} 标签）。 */
    private String robotsTemplate(FileTemplateSource source) {
        var template = source.load("robots.txt");
        if (template == null) {
            return null;
        }
        return template.source();
    }

    private static String batchIdOf(long siteId) {
        return "b" + siteId + "-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now()) + "-" + Long.toString(System.nanoTime() % 100000, 36);
    }

    private static String sha256(String text) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 站点目录下的路径检查（供控制器与后台用）。 */
    public static void assertSiteDir(Path siteDir) {
        SitePathBoundary.assertWritable(siteDir);
    }

    /** 发布前的"能不能发布"预检：主题目录与模板都到位吗（不渲染）。 */
    public List<String> preflight(long siteId, Path siteDir, ContentProvider provider) {
        List<String> problems = new ArrayList<>();
        String theme = themeOf(provider.site());
        Path themeRoot;
        try {
            // 与 publish 同一条路径边界：主题名含 .. / 绝对路径时在这里就报出来（§7.8）
            themeRoot = SitePathBoundary.resolveUnder(siteDir.resolve("template"), theme);
        } catch (BizException e) {
            problems.add("站点主题名不合法：" + theme + "（" + e.getMessage() + "）");
            return problems;
        }
        if (!Files.isDirectory(themeRoot)) {
            problems.add("主题目录不存在：" + themeRoot);
            return problems;
        }
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(new FileTemplateSource(themeRoot), theme);
        if (lookup.find(PageType.HOME, null, null, null) == null) {
            problems.add("主题缺少首页模板 index.html");
        }
        // list.html 是**列表类页面**的兜底：分类索引页、标签总览、标签详情、归档、筛选页，
        // 以及任何声明了 list_url_pattern 的类型列表页，找不到各自模板时都落到它。
        // 所以只在"这个站点真可能出这类页面"时才要求它在场——一个没有栏目、没有标签、
        // 只有单页与详情页的官网不需要 list.html，无脑要求会把那种站点直接挡在发布之外
        // （而它本来一个列表页都不会计划出来）。
        if (needsListFallback(provider.site(), provider)
                && lookup.find(PageType.LIST, null, null, null) == null) {
            problems.add("主题缺少列表兜底模板 list.html");
        }
        return problems;
    }

    /** 本站点是否可能出现"列表类页面"——决定 {@code list.html} 是不是必需模板。 */
    private static boolean needsListFallback(SiteConfig site, ContentProvider provider) {
        if (site.flag("page.category", true) || site.flag("page.taglist", true)
                || site.flag("page.tag", true) || site.flag("page.archive", false)
                || site.flag("page.facet", false)) {
            return true;
        }
        for (ContentTypeDef type : provider.types()) {
            if (type.listUrlPattern() != null && !type.listUrlPattern().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
