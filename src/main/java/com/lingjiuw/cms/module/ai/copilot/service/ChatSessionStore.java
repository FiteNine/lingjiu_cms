package com.lingjiuw.cms.module.ai.copilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话的热态（docs/ai-copilot.md §2、§5.2）。
 *
 * <p>只有两样东西放内存：**危险操作确认闸门的 future** 与**取消标记**。
 * 消息一律入库（追加日志），因为回放与审计都要跨请求、跨进程重启。
 *
 * <p>为什么不用 Caffeine：会话 TTL 用一个 ConcurrentHashMap + 惰性清理足够（§7.10 零新增依赖）。
 */
@Slf4j
@Component
public class ChatSessionStore {

    /** 确认超时：超时按 DENY 处理，回灌"用户未在时限内确认"让模型换路子 */
    public static final long CONFIRM_TIMEOUT_MINUTES = 5;

    private static final long IDLE_EVICT_MILLIS = 30 * 60 * 1000L;

    private final Map<Long, ActiveSession> sessions = new ConcurrentHashMap<>();

    /**
     * 确认闸门的结果。
     *
     * @param allow        是否批准
     * @param argsOverride 用户在弹窗里改过的参数；为空表示按模型原参数执行
     * @param reason       拒绝原因（用户拒绝 / 超时 / 会话已结束），会回灌给模型
     */
    public record ConfirmDecision(boolean allow, JsonNode argsOverride, String reason) {

        public static ConfirmDecision allow(JsonNode argsOverride) {
            return new ConfirmDecision(true, argsOverride, null);
        }

        public static ConfirmDecision deny(String reason) {
            return new ConfirmDecision(false, null, reason);
        }
    }

    /** 会话开始前登记；会话结束时 {@link #close}。 */
    public void open(long sessionId) {
        evictIdle();
        sessions.computeIfAbsent(sessionId, key -> new ActiveSession());
    }

    /** 登记一次待确认的工具调用，返回等待结果的 future。 */
    public CompletableFuture<ConfirmDecision> awaitConfirm(long sessionId, String toolCallId) {
        ActiveSession session = sessions.computeIfAbsent(sessionId, key -> new ActiveSession());
        CompletableFuture<ConfirmDecision> future = new CompletableFuture<>();
        session.pending = new Pending(toolCallId, future);
        session.touch();
        return future;
    }

    /** 由 {@code /api/ai/copilot/confirm} 调用；toolCallId 对不上或没有待确认项时返回 false。 */
    public boolean complete(long sessionId, String toolCallId, ConfirmDecision decision) {
        ActiveSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        Pending pending = session.pending;
        if (pending == null || !pending.toolCallId.equals(toolCallId)) {
            return false;
        }
        session.touch();
        return pending.future.complete(decision);
    }

    public void clearPending(long sessionId) {
        ActiveSession session = sessions.get(sessionId);
        if (session != null) {
            session.pending = null;
        }
    }

    /** 用户关掉标签页 → SSE onError/onCompletion → 循环每次迭代前检查它 */
    public void cancel(long sessionId) {
        ActiveSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        session.cancelled.set(true);
        Pending pending = session.pending;
        if (pending != null) {
            // 会话已断，闸门不会再被打开：直接以拒绝收场，别让循环卡到超时
            pending.future.complete(ConfirmDecision.deny("会话已结束，操作已取消"));
        }
    }

    public boolean isCancelled(long sessionId) {
        ActiveSession session = sessions.get(sessionId);
        return session != null && session.cancelled.get();
    }

    public void close(long sessionId) {
        cancel(sessionId);
        sessions.remove(sessionId);
    }

    private void evictIdle() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            ActiveSession session = entry.getValue();
            boolean idle = session.pending == null && now - session.lastAccess > IDLE_EVICT_MILLIS;
            if (idle) {
                session.cancelled.set(true);
            }
            return idle;
        });
    }

    private record Pending(String toolCallId, CompletableFuture<ConfirmDecision> future) {
    }

    private static final class ActiveSession {
        private volatile Pending pending;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile long lastAccess = System.currentTimeMillis();

        private void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }
}
