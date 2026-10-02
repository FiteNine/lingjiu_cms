package com.lingjiuw.cms.module.ai.copilot.service;

/**
 * SSE 出口。抽成接口是为了让 {@link AgentLoopService} 不直接依赖 {@code SseEmitter}，
 * 出站事件的名字与载荷一眼可见（docs/ai-copilot.md §5.1 的事件表）。
 */
public interface CopilotSink {

    void send(String event, Object data);
}
