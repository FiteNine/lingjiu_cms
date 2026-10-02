package com.lingjiuw.cms.module.ai.copilot.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.module.ai.copilot.model.ToolCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式 tool_calls 分片拼接的单元测试（docs/ai-copilot.md §7.1.1）。
 *
 * <p>实测依据：一次 48 字符的 {@code arguments} 跨了 27 个分片到达，
 * 且 {@code function.arguments} 是跨片截断的 JSON 字符串，**不能边收边 parse**。
 */
class OpenAiToolProtocolClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 只收增量，不做事。 */
    private static final class Collector implements ToolStreamListener {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();

        @Override
        public void onReasoning(String delta) {
            reasoning.append(delta);
        }

        @Override
        public void onDelta(String delta) {
            content.append(delta);
        }

        @Override
        public void onFinish(ToolStreamResult result) {
        }
    }

    @Test
    void 跨多个分片的工具参数能拼回完整JSON() throws Exception {
        OpenAiToolProtocolClient.DeltaAccumulator accumulator = new OpenAiToolProtocolClient.DeltaAccumulator();
        Collector collector = new Collector();

        // 首片给全 id 与 name，arguments 被切成 27 段（模拟真实到达形状）
        accumulator.accept(chunk("""
                {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_00_abc","type":"function","function":{"name":"cms_content_list","arguments":"{\\"ty"}}]}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"peCode\\": \\"ar"}}]}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ticle\\", \\"status\\": \\"PUBLISHED\\", \\"size\\": 5}"}}]}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[{"index":0,"finish_reason":"tool_calls","delta":{}}],"usage":{"prompt_tokens":590,"completion_tokens":80}}
                """), collector);

        ToolStreamResult result = accumulator.result();
        assertEquals("tool_calls", result.finishReason());
        assertTrue(result.hasToolCalls());
        assertEquals(1, result.toolCalls().size());

        ToolCall call = result.toolCalls().get(0);
        // tool_call_id 必须原样保留（截断/重命名会让服务端在下一次回灌时报 400，见 §10.4）
        assertEquals("call_00_abc", call.id());
        assertEquals("cms_content_list", call.name());

        JsonNode args = objectMapper.readTree(call.arguments());
        assertEquals("article", args.path("typeCode").asText());
        assertEquals("PUBLISHED", args.path("status").asText());
        assertEquals(5, args.path("size").asInt());

        assertEquals(590, result.inputTokens());
        assertEquals(80, result.outputTokens());
    }

    @Test
    void 两个工具调用按index分片互不串扰() throws Exception {
        OpenAiToolProtocolClient.DeltaAccumulator accumulator = new OpenAiToolProtocolClient.DeltaAccumulator();
        Collector collector = new Collector();

        accumulator.accept(chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call_00_a","function":{"name":"cms_stats","arguments":"{}"}},
                  {"index":1,"id":"call_01_b","function":{"name":"cms_category_tree","arguments":"{}"}}]}}]}
                """), collector);

        ToolStreamResult result = accumulator.result();
        assertEquals(2, result.toolCalls().size());
        assertEquals("call_00_a", result.toolCalls().get(0).id());
        assertEquals("cms_stats", result.toolCalls().get(0).name());
        assertEquals("call_01_b", result.toolCalls().get(1).id());
        assertEquals("cms_category_tree", result.toolCalls().get(1).name());
    }

    @Test
    void 截断的参数原样返回而不是抛异常() throws Exception {
        OpenAiToolProtocolClient.DeltaAccumulator accumulator = new OpenAiToolProtocolClient.DeltaAccumulator();
        Collector collector = new Collector();

        // 上游掉包导致 JSON 截断：累加器不回退、不报错，把原文交出去；
        // "参数不是合法 JSON" 由循环回灌成一条 isError 工具结果（§7.1.1）
        accumulator.accept(chunk("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_00_x","function":{"name":"cms_content_get","arguments":"{\\"id\\":"}}]}}]}
                """), collector);

        ToolCall call = accumulator.result().toolCalls().get(0);
        assertEquals("{\"id\":", call.arguments());
        assertThrows(Exception.class, () -> objectMapper.readTree(call.arguments()));
    }

    @Test
    void 正文与思考增量分别累加() throws Exception {
        OpenAiToolProtocolClient.DeltaAccumulator accumulator = new OpenAiToolProtocolClient.DeltaAccumulator();
        Collector collector = new Collector();

        accumulator.accept(chunk("""
                {"choices":[{"delta":{"reasoning_content":"先想一下"}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[{"delta":{"content":"好"}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[{"delta":{"content":"的"}}]}
                """), collector);

        assertEquals("先想一下", collector.reasoning.toString());
        assertEquals("好的", collector.content.toString());
        ToolStreamResult result = accumulator.result();
        assertFalse(result.hasToolCalls());
    }

    @Test
    void 只有usage的尾帧不算无输出() throws Exception {
        OpenAiToolProtocolClient.DeltaAccumulator accumulator = new OpenAiToolProtocolClient.DeltaAccumulator();
        Collector collector = new Collector();

        accumulator.accept(chunk("""
                {"choices":[{"delta":{"content":"hi"}}]}
                """), collector);
        accumulator.accept(chunk("""
                {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":3}}
                """), collector);

        assertEquals(12, accumulator.result().inputTokens());
        assertEquals(3, accumulator.result().outputTokens());
    }

    private JsonNode chunk(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
