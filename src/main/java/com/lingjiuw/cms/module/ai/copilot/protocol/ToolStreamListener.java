package com.lingjiuw.cms.module.ai.copilot.protocol;

/**
 * 流式对话的回调。正文与思考增量实时推给前端，工具调用在流结束后一次性给出
 * （{@code function.arguments} 是跨片截断的 JSON 字符串，攒完才能解析，见 docs/ai-copilot.md §7.1.1）。
 */
public interface ToolStreamListener {

    /** 思考内容增量 */
    void onReasoning(String delta);

    /** 正文增量 */
    void onDelta(String delta);

    /** 一轮结束；工具调用已拼装完成 */
    void onFinish(ToolStreamResult result);
}
