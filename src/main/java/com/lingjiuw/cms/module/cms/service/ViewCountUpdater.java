package com.lingjiuw.cms.module.cms.service;

import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 浏览量异步自增：公开详情 GET 如果在读路径上同步写 cms_content，热门文章的并发访问会挤在同一行的
 * 锁上（L-3）。这里用「单线程 + 有界队列」把写库动作挪出请求线程：写库串行，队列满时直接丢弃
 * （浏览量是近似值，不值得为它阻塞请求或放大线程数）。
 *
 * <p>不用 {@code @Async}：同类内直接调用不经过 Spring 代理，异步不会生效；专用线程池更直白，
 * 也便于在应用关闭时优雅收敛。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountUpdater {

    private final CmsContentMapper contentMapper;

    /** 单线程 + 有界队列（1000）：写库串行避免锁竞争，拒绝策略只记 debug 不抛异常 */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000),
            runnable -> {
                Thread thread = new Thread(runnable, "cms-view-count");
                thread.setDaemon(true);
                return thread;
            },
            (task, pool) -> log.debug("浏览量自增任务被丢弃：队列已满或线程池已关闭"));

    /** 提交一次浏览量 +1；任务失败或队列满都只记日志，不影响公开接口返回 */
    public void increment(Long id) {
        executor.execute(() -> {
            try {
                contentMapper.increaseViewCount(id);
            } catch (Exception e) {
                // 浏览量不是业务数据：写失败不能冒泡（任务已在请求线程之外，异常只会丢进线程池）
                log.warn("浏览量自增失败，contentId={}", id, e);
            }
        });
    }

    /** 关闭时停收新任务，给队列里的写入留一个短暂窗口，超时强制中断 */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
