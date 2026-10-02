package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import com.lingjiuw.cms.module.cms.publish.service.PublishFacade;
import com.lingjiuw.cms.module.cms.publish.service.SitePublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 后台发布批次的在途登记（docs/ai-copilot.md §4.4、§7.8）。
 *
 * <p><b>为什么要有它</b>：全站静态化发布同步阻塞数秒到数分钟，直接塞进 tool loop 会让
 * SSE 长时间静默、前端像卡死。所以 {@code cms_publish_run} 用独立线程提交发布、立即返回 runId，
 * 模型再用 {@code cms_publish_status} 轮询。
 *
 * <p>为什么不用 {@code batchId}：{@code batchId} 是 {@code SitePublishService} 在 doPublish
 * 内部生成的，提交时还不知道；而且任务行是发布结束后才落库，发布中查不到"正在跑"的那一批。
 * 这里用自己签发的 runId 把在途状态留在内存，发布结果另行落 {@code cms_publish_task}。
 *
 * <p>并发安全不需要额外加锁：{@code SitePublishService} 内部有 per-site {@code ReentrantLock}，
 * 后台发布与人工发布会正确排队。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishRunRegistry {

    /** 在途/已完成记录的保留时长，超时在下次访问时惰性清理 */
    private static final long RETAIN_MILLIS = 2 * 60 * 60 * 1000L;

    private final PublishFacade publishFacade;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "copilot-publish");
        thread.setDaemon(true);
        return thread;
    });

    /** 一条发布记录对外可见的快照。 */
    public record RunStatus(String runId, long siteId, String mode, String state, String batchId,
                            int totalPages, int writtenPages, int failedPages, int deletedArtifacts,
                            long elapsedMillis, String error) {
    }

    public RunStatus start(long siteId, Path siteDir, SitePublishService.Mode mode) {
        evictExpired();
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        Run run = new Run(runId, siteId, mode);
        runs.put(runId, run);
        executor.submit(() -> {
            try {
                SitePublishService.PublishResult result = publishFacade.publish(siteId, siteDir, mode, "ai");
                run.finish(result);
            } catch (Exception e) {
                log.warn("后台发布失败 runId={} siteId={}", runId, siteId, e);
                run.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
        return run.status();
    }

    public RunStatus status(String runId) {
        Run run = runs.get(runId);
        return run == null ? null : run.status();
    }

    public List<RunStatus> recent(int limit) {
        List<Run> sorted = new ArrayList<>(runs.values());
        sorted.sort(Comparator.comparingLong(Run::startedAt).reversed());
        return sorted.stream().limit(Math.max(1, Math.min(limit, 20))).map(Run::status).toList();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        runs.values().removeIf(run -> run.finishedAt() > 0 && now - run.finishedAt() > RETAIN_MILLIS);
    }

    /** 在途状态；字段用 volatile 保证后台线程写完对请求线程可见。 */
    private static final class Run {

        private final String runId;
        private final long siteId;
        private final SitePublishService.Mode mode;
        private final long startedAt = System.currentTimeMillis();
        private volatile String state = "RUNNING";
        private volatile String batchId;
        private volatile int totalPages;
        private volatile int writtenPages;
        private volatile int failedPages;
        private volatile int deletedArtifacts;
        private volatile long finishedAt;
        private volatile String error;

        private Run(String runId, long siteId, SitePublishService.Mode mode) {
            this.runId = runId;
            this.siteId = siteId;
            this.mode = mode;
        }

        private long startedAt() {
            return startedAt;
        }

        private long finishedAt() {
            return finishedAt;
        }

        private void finish(SitePublishService.PublishResult result) {
            this.batchId = result.batchId();
            this.totalPages = result.totalPages();
            this.writtenPages = result.writtenPages();
            this.failedPages = result.failedPages();
            this.deletedArtifacts = result.deletedArtifacts();
            this.state = result.success() ? "SUCCESS" : "PARTIAL";
            this.finishedAt = System.currentTimeMillis();
        }

        private void fail(String message) {
            this.state = "FAILED";
            this.error = message;
            this.finishedAt = System.currentTimeMillis();
        }

        private RunStatus status() {
            return new RunStatus(runId, siteId, mode.name(), state, batchId, totalPages, writtenPages,
                    failedPages, deletedArtifacts, System.currentTimeMillis() - startedAt, error);
        }
    }

    /** 供诊断看的在途条数。 */
    int activeCount() {
        return runs.size();
    }
}
