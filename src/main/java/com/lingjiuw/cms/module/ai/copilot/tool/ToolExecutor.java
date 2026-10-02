package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingjiuw.cms.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具执行器：**只读并发、写串行**（docs/ai-copilot.md §7.4）。
 *
 * <p>已实测确认模型会一次返回多个 {@code tool_calls}（一次 {@code cms_stats}
 * + 一次 {@code cms_category_tree}），所以并发执行不是空想优化——一句话问到两个信息源时，
 * 串行就是白等一个来回。写工具串行则是因为并发写会在不同事务里抢同一行，收益为零风险不小。
 *
 * <p>并发度 4：Hikari 默认 10 连接，工具并发上限必须远小于它，
 * 否则一次 AI 对话就能把连接池占满、正常后台用户全部超时（§7.5）。
 *
 * <p>每个子线程都要显式恢复上下文——ThreadLocal 不会自己传播，而这里所有执行都经
 * {@link ToolContext#call}，上下文处理只在这一处。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    /** 工具并发上限：跟着数据库连接池走，别把 Hikari 占满 */
    private static final int READ_CONCURRENCY = 4;

    /** 回灌给模型的内容上限：即便某个工具没瘦身，也不会把上下文撑爆 */
    private static final int MAX_MODEL_CONTENT = 30000;

    private final ToolPermission permission;
    private final ToolAudit audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final ExecutorService readPool = Executors.newFixedThreadPool(
            READ_CONCURRENCY, daemonFactory("copilot-tool-"));

    /** 按调用顺序返回结果（前端与回灌都依赖这个顺序）。 */
    public List<ToolExecutionResult> executeAll(List<ToolCallRequest> calls, ToolContext ctx) {
        List<ToolExecutionResult> results = new ArrayList<>(calls.size());
        List<Integer> readSlots = new ArrayList<>();
        List<Future<ToolExecutionResult>> futures = new ArrayList<>();
        List<ToolCallRequest> reads = new ArrayList<>();
        List<Integer> writeSlots = new ArrayList<>();
        List<ToolCallRequest> writes = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolCallRequest call = calls.get(i);
            results.add(null);
            if (!permission.allowed(call.spec(), ctx.perms())) {
                results.set(i, forbidden(call, ctx));
                continue;
            }
            if (call.spec().risk() == ToolRisk.READ) {
                readSlots.add(i);
                reads.add(call);
            } else {
                writeSlots.add(i);
                writes.add(call);
            }
        }
        for (ToolCallRequest call : reads) {
            futures.add(readPool.submit(() -> executeOne(call, ctx)));
        }
        for (int i = 0; i < futures.size(); i++) {
            int slot = readSlots.get(i);
            try {
                results.set(slot, futures.get(i).get());
            } catch (Exception e) {
                results.set(slot, failure(reads.get(i), ctx, e));
            }
        }
        // 写工具串行：同一个 SSE 请求内一次只跑一个写
        for (int i = 0; i < writes.size(); i++) {
            results.set(writeSlots.get(i), executeOne(writes.get(i), ctx));
        }
        return results;
    }

    private ToolExecutionResult executeOne(ToolCallRequest call, ToolContext ctx) {
        long start = System.currentTimeMillis();
        ToolResult result;
        try {
            result = ctx.call(() -> call.spec().handler().execute(call.args()));
        } catch (BizException e) {
            result = ToolResult.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("工具执行失败：{}", call.name(), e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            result = ToolResult.fail("执行失败：" + message);
        }
        long duration = System.currentTimeMillis() - start;
        String content = content(result, ctx);
        ToolExecutionResult exec = new ToolExecutionResult(call.toolCallId(), call.spec().name(),
                call.spec().title(), result.ok(), result.summary(), content, result.error(),
                duration, ctx.siteId(), result.ok() ? ToolExecutionResult.STATUS_OK
                        : ToolExecutionResult.STATUS_FAIL, call.spec().risk(), result.data());
        if (call.spec().risk() != ToolRisk.READ) {
            audit.record(ctx, call, exec);
        }
        return exec;
    }

    private ToolExecutionResult forbidden(ToolCallRequest call, ToolContext ctx) {
        String error = "当前用户没有权限调用该工具（需要权限：" + call.spec().permission() + "）";
        return new ToolExecutionResult(call.toolCallId(), call.spec().name(), call.spec().title(),
                false, null, error, error, 0L, ctx.siteId(),
                ToolExecutionResult.STATUS_FORBIDDEN, call.spec().risk(), null);
    }

    private ToolExecutionResult failure(ToolCallRequest call, ToolContext ctx, Exception e) {
        String error = "工具执行异常：" + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        return new ToolExecutionResult(call.toolCallId(), call.spec().name(), call.spec().title(),
                false, null, error, error, 0L, ctx.siteId(),
                ToolExecutionResult.STATUS_FAIL, call.spec().risk(), null);
    }

    /**
     * 回灌给模型的内容。统一包一层信封：**每个工具结果都回显实际生效的 siteId 与站点名**
     * （docs/ai-copilot.md 铁律 3）——不让"AI 说写成功了、实际写进了默认站点"发生。
     */
    private String content(ToolResult result, ToolContext ctx) {
        if (!result.ok()) {
            return result.error() == null ? "工具执行失败" : result.error();
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("siteId", ctx.siteId());
        envelope.put("siteName", ctx.siteName());
        if (result.summary() != null) {
            envelope.put("summary", result.summary());
        }
        if (result.data() != null) {
            envelope.set("data", result.data());
        }
        String text = envelope.toString();
        return text.length() > MAX_MODEL_CONTENT ? text.substring(0, MAX_MODEL_CONTENT) + "...(已截断)" : text;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        readPool.shutdownNow();
    }
}
