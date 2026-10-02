package com.lingjiuw.cms.module.ai.copilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.security.SecurityUtils;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotChatRequest;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotConfirmRequest;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotSessionDetailVO;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotSessionVO;
import com.lingjiuw.cms.module.ai.copilot.service.AgentLoopService;
import com.lingjiuw.cms.module.ai.copilot.service.ChatSessionStore;
import com.lingjiuw.cms.module.ai.copilot.service.CopilotService;
import com.lingjiuw.cms.module.ai.copilot.service.CopilotSink;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全站 agent 接口（docs/ai-copilot.md §5）。
 *
 * <p><b>用 POST + fetch 流，不用 {@code EventSource}</b>：{@code EventSource} 不能带
 * {@code Authorization} 头，而 JWT 只能在头里传。
 *
 * <p><b>上下文捕获是这一层的核心</b>：在**请求线程上**先拿到 LoginUser，再在 {@code prepare}
 * 里把收敛后的 siteId 与 perms 建成 {@link com.lingjiuw.cms.module.ai.copilot.tool.ToolContext}，
 * 交给虚拟线程上的循环。循环跑起来时请求线程早已返回，两个 ThreadLocal 都是空的。
 *
 * <p>线程模型：只在 copilot 模块内用虚拟线程，不动 {@code spring.threads.virtual.enabled}
 * ——发布引擎、预览服务里有 {@code synchronized}，全局开虚拟线程会撞 pinning（§7.3）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/copilot")
@RequiredArgsConstructor
public class CopilotController {

    /** 心跳间隔：上游长思考 + 反向代理容易在 60s 空闲时断连 */
    private static final long HEARTBEAT_MILLIS = 15_000L;
    /** SSE 连接上限：覆盖"最长 5 分钟等确认 + 若干轮生成"后仍有一个明确的收尾点 */
    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 批准危险操作需要的权限（只读用户能聊但批不了删除） */
    private static final String CONFIRM_PERMISSION = "ai:copilot:confirm";

    private final CopilotService copilotService;
    private final AgentLoopService agentLoopService;
    private final ChatSessionStore store;
    private final ObjectMapper objectMapper;

    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 一轮对话（SSE）。
     *
     * <p>刻意**不加 {@code produces = text/event-stream}**：返回 {@code SseEmitter} 本身就会把
     * 响应设成 {@code text/event-stream}；而业务校验失败时由 GlobalExceptionHandler 返回 JSON
     * 统一响应包，若这里锁死 produces，错误响应会因内容协商失败变成 406。
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('ai:copilot:chat')")
    public SseEmitter chat(@RequestBody CopilotChatRequest request) {
        LoginUser user = SecurityUtils.user();
        long siteId = copilotService.resolveSite(SiteContext.siteId());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        SseSink sink = new SseSink(emitter, objectMapper, cancelled);
        // 会话 id 在 prepare 之后才知道；取消回调按这个 holder 找会话
        long[] sessionId = new long[]{-1L};
        emitter.onCompletion(() -> cancel(sessionId, cancelled));
        emitter.onError(error -> cancel(sessionId, cancelled));
        emitter.onTimeout(() -> cancel(sessionId, cancelled));

        startHeartbeat(sink, cancelled);
        virtualThreads.submit(() -> {
            try {
                CopilotService.PreparedChat prepared = copilotService.prepare(request, user, siteId);
                sessionId[0] = prepared.sessionId();
                sink.send("session", sessionPayload(prepared));
                agentLoopService.run(new AgentLoopService.CopilotRun(prepared.sessionId(), prepared.agent(),
                        prepared.provider(), prepared.tools(), prepared.ctx()), sink, cancelled);
                emitter.complete();
            } catch (BizException e) {
                sink.send("error", errorPayload(e.getCode(), e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("全站 agent 对话异常 sessionId={}", sessionId[0], e);
                sink.send("error", errorPayload(500, "系统繁忙，请稍后重试"));
                emitter.complete();
            } finally {
                if (sessionId[0] > 0) {
                    store.close(sessionId[0]);
                }
            }
        });
        return emitter;
    }

    /** 危险操作的人工确认（docs/ai-copilot.md §5.2）。 */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('ai:copilot:chat')")
    public Result<Void> confirm(@RequestBody @Valid CopilotConfirmRequest request) {
        LoginUser user = SecurityUtils.user();
        if (request.allow() && !hasPermission(user, CONFIRM_PERMISSION)) {
            throw new BizException(403, "你没有确认危险操作的权限");
        }
        ChatSessionStore.ConfirmDecision decision = request.allow()
                ? ChatSessionStore.ConfirmDecision.allow(request.argsOverride())
                : ChatSessionStore.ConfirmDecision.deny("用户拒绝了该操作");
        if (!store.complete(request.sessionId(), request.toolCallId(), decision)) {
            throw new BizException("该确认已失效（可能已超时、已被处理，或会话已结束）");
        }
        return Result.ok();
    }

    /** 本人会话分页 */
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('ai:copilot:chat')")
    public Result<PageResult<CopilotSessionVO>> sessions(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size) {
        return Result.ok(copilotService.sessions(page, size, SecurityUtils.userId()));
    }

    /** 会话详情（含全部消息，用于回放） */
    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('ai:copilot:chat')")
    public Result<CopilotSessionDetailVO> session(@PathVariable Long id) {
        return Result.ok(copilotService.sessionDetail(id, SecurityUtils.userId()));
    }

    /* ---------------- SSE 工具 ---------------- */

    private Map<String, Object> sessionPayload(CopilotService.PreparedChat prepared) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", prepared.sessionId());
        payload.put("siteId", prepared.ctx().siteId());
        payload.put("siteName", prepared.ctx().siteName());
        payload.put("agentId", prepared.agent().getId());
        payload.put("agentName", prepared.agent().getName());
        List<Map<String, String>> tools = new ArrayList<>();
        prepared.tools().forEach(spec -> tools.add(Map.of(
                "name", spec.name(), "title", spec.title(), "risk", spec.risk().name())));
        payload.put("tools", tools);
        return payload;
    }

    private Map<String, Object> errorPayload(int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message == null ? "系统繁忙，请稍后重试" : message);
        return payload;
    }

    /** 心跳：每 15 秒一个注释帧（`:...`），前端按 SSE 规范忽略它。 */
    private void startHeartbeat(SseSink sink, AtomicBoolean cancelled) {
        Thread heartbeat = new Thread(() -> {
            try {
                while (!cancelled.get()) {
                    Thread.sleep(HEARTBEAT_MILLIS);
                    if (cancelled.get()) {
                        return;
                    }
                    sink.comment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "copilot-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private void cancel(long[] sessionId, AtomicBoolean cancelled) {
        cancelled.set(true);
        if (sessionId[0] > 0) {
            store.cancel(sessionId[0]);
        }
    }

    /** 权限判定：与 ToolPermission 同一把尺子——纯 perms，roles 里没有 admin 旁路。 */
    private static boolean hasPermission(LoginUser user, String permission) {
        return user.getPerms() != null && user.getPerms().contains(permission);
    }

    @PreDestroy
    void shutdown() {
        virtualThreads.shutdownNow();
    }

    /**
     * SSE 出口。{@link SseEmitter} 不是线程安全的，而循环（虚拟线程）与心跳线程都会写它，
     * 因此所有写出都过同一把锁；一旦写出失败就置取消标记——用户关掉标签页不该继续烧 token。
     */
    private static final class SseSink implements CopilotSink {

        private final SseEmitter emitter;
        private final ObjectMapper objectMapper;
        private final AtomicBoolean cancelled;
        private final Object lock = new Object();

        private SseSink(SseEmitter emitter, ObjectMapper objectMapper, AtomicBoolean cancelled) {
            this.emitter = emitter;
            this.objectMapper = objectMapper;
            this.cancelled = cancelled;
        }

        @Override
        public void send(String event, Object data) {
            if (cancelled.get()) {
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(data);
                write(SseEmitter.event().name(event).data(json));
            } catch (Exception e) {
                cancel();
            }
        }

        void comment() {
            if (cancelled.get()) {
                return;
            }
            try {
                write(SseEmitter.event().comment(""));
            } catch (Exception e) {
                cancel();
            }
        }

        private void write(SseEmitter.SseEventBuilder builder) throws Exception {
            synchronized (lock) {
                emitter.send(builder);
            }
        }

        private void cancel() {
            cancelled.set(true);
        }
    }
}
