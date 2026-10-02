package com.lingjiuw.cms.module.ai.copilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.copilot.entity.AiChatMessage;
import com.lingjiuw.cms.module.ai.copilot.entity.AiChatSession;
import com.lingjiuw.cms.module.ai.copilot.mapper.AiChatMessageMapper;
import com.lingjiuw.cms.module.ai.copilot.mapper.AiChatSessionMapper;
import com.lingjiuw.cms.module.ai.copilot.model.CopilotMessage;
import com.lingjiuw.cms.module.ai.copilot.model.ToolCall;
import com.lingjiuw.cms.module.ai.copilot.protocol.AiToolProtocolClient;
import com.lingjiuw.cms.module.ai.copilot.protocol.ToolSchemaView;
import com.lingjiuw.cms.module.ai.copilot.protocol.ToolStreamListener;
import com.lingjiuw.cms.module.ai.copilot.protocol.ToolStreamResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolCallRequest;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolContext;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolExecutionResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolExecutor;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolPermission;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRegistry;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRisk;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.entity.AiAgent;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 工具调用循环（docs/ai-copilot.md §2、§7）。
 *
 * <p>一轮 = 调模型（带 tools、流式）→ 取 tool_calls → 权限预检 → 确认闸门 → 执行 → 回灌。
 * 上限 8 轮，全局 {@code Semaphore(8)}，可取消。
 *
 * <p>跑在虚拟线程上（由 {@code CopilotController} 提交）：一个可能挂 5 分钟的 SSE +
 * 一次等用户确认，不占 Tomcat 平台线程。两条 ThreadLocal（站点 + 安全上下文）由
 * {@link ToolContext} 显式恢复，绝不改 {@code MODE_INHERITABLETHREADLOCAL}。
 */
@Slf4j
@Service
public class AgentLoopService {

    /** 单次对话最多几轮工具循环 */
    private static final int MAX_ROUNDS = 8;
    /** 全局并发上限：Hikari 默认 10 连接，工具并发 4，循环总数必须留出余量（§7.5） */
    private static final Semaphore CONCURRENCY = new Semaphore(8);
    /** 缺少这个权限的用户不能批准危险操作（只读用户能聊但批不了删除） */
    private static final String CONFIRM_PERMISSION = "ai:copilot:confirm";
    /** 发布确认弹窗要列出的工具名：本会话内改过的模板文件 */
    private static final String REPLACE_TOOL = "cms_site_file_replace";

    private static final String PLATFORM_PROMPT = """
            你是「凌久网 CMS」后台的全站智能体，用自然语言帮用户完成站点操作。

            当前站点：%s（siteId=%d）

            规则：
            1. 只能用提供的工具操作数据；没有对应工具的能力不要承诺，也不要编造结果。
            2. 所有工具都只在当前站点内生效，工具返回值里的 siteId 就是实际生效的站点。
            3. 写操作与破坏性操作需要用户在界面上确认。被拒绝时不要重试同一个调用，换个思路或直接询问用户。
            4. 一次只处理一条记录，不要臆造 id —— 先用查询工具拿到真实 id 再写。
            5. 参数必须符合工具声明的 JSON Schema。
            6. 回答简洁、用中文；先说结论，再给必要细节。
            """;

    private final AiChatMessageMapper messageMapper;
    private final AiChatSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;
    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final ToolPermission permission;
    private final ChatSessionStore store;
    private final Map<String, AiToolProtocolClient> clients;

    public AgentLoopService(AiChatMessageMapper messageMapper, AiChatSessionMapper sessionMapper,
                            ObjectMapper objectMapper, ToolRegistry registry, ToolExecutor executor,
                            ToolPermission permission, ChatSessionStore store,
                            List<AiToolProtocolClient> clients) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.executor = executor;
        this.permission = permission;
        this.store = store;
        this.clients = clients.stream().collect(Collectors.toMap(
                client -> client.protocol().name(), Function.identity(), (existing, duplicate) -> {
                    throw new BizException("协议「" + existing.protocol().name() + "」存在多个工具客户端实现");
                }));
    }

    /** 一次对话的输入。{@code tools} 已按角色权限与 {@code ai_agent.tool_scope} 过滤。 */
    public record CopilotRun(long sessionId, AiAgent agent, AiProvider provider, List<ToolSpec> tools,
                             ToolContext ctx) {
    }

    /** 本轮结束后的统计，供控制器回传给前端。 */
    public record RunSummary(int rounds, int toolCalls, int inputTokens, int outputTokens, long lastMessageId) {
    }

    public void run(CopilotRun run, CopilotSink sink, AtomicBoolean cancelled) {
        if (!CONCURRENCY.tryAcquire()) {
            throw new BizException("当前 AI 对话并发已满，请稍后再试");
        }
        store.open(run.sessionId());
        try {
            execute(run, sink, cancelled);
        } finally {
            CONCURRENCY.release();
        }
    }

    private void execute(CopilotRun run, CopilotSink sink, AtomicBoolean cancelled) {
        List<CopilotMessage> messages = history(run.sessionId());
        int rounds = 0;
        int toolCallCount = 0;
        int inputTokens = 0;
        int outputTokens = 0;
        long lastAssistantId = 0L;

        for (int round = 1; round <= MAX_ROUNDS && !cancelled.get(); round++) {
            rounds++;
            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            ToolStreamResult stream = streamOnce(run, messages, sink, cancelled, content, reasoning);
            inputTokens += nz(stream.inputTokens());
            outputTokens += nz(stream.outputTokens());

            List<ToolCall> toolCalls = stream.toolCalls() == null ? List.of() : stream.toolCalls();
            lastAssistantId = saveAssistant(run, content.toString(), reasoning.toString(), toolCalls);
            messages.add(CopilotMessage.assistant(content.toString(), reasoning.toString(), toolCalls));
            if (toolCalls.isEmpty()) {
                break;
            }

            // 达到轮次上限时不再执行，但必须把每个 tool_call 补上响应，否则历史里的
            // assistant.tool_calls 会永远没有对应的 tool 消息（下次请求服务端会报 id 对不上）
            boolean overLimit = round == MAX_ROUNDS;
            List<ToolExecutionResult> results = executeToolCalls(run, toolCalls, messages, sink,
                    cancelled, overLimit);
            toolCallCount += toolCalls.size();
            for (ToolExecutionResult result : results) {
                emitToolResult(sink, result);
                saveToolMessage(run, result);
                messages.add(CopilotMessage.tool(result.toolCallId(), result.content()));
            }
        }

        finishSession(run.sessionId(), rounds, toolCallCount, inputTokens, outputTokens);
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("messageId", lastAssistantId);
        done.put("inputTokens", inputTokens);
        done.put("outputTokens", outputTokens);
        done.put("rounds", rounds);
        sink.send("done", done);
    }

    /* ---------------- 调模型 ---------------- */

    private ToolStreamResult streamOnce(CopilotRun run, List<CopilotMessage> messages, CopilotSink sink,
                                        AtomicBoolean cancelled, StringBuilder content, StringBuilder reasoning) {
        AiToolProtocolClient client = clients.get(AiProtocol.OPENAI.name());
        if (client == null) {
            throw new BizException("协议「OPENAI」暂不支持工具调用，请在「AI服务商」里改用 OPENAI 协议的配置");
        }
        List<ToolSchemaView> schemas = run.tools().stream()
                .map(spec -> new ToolSchemaView(spec.name(), spec.description(), spec.inputSchema()))
                .toList();
        ToolStreamResult[] holder = new ToolStreamResult[1];
        client.stream(run.provider(), optionsOf(run), messages, schemas, new ToolStreamListener() {
            @Override
            public void onReasoning(String delta) {
                reasoning.append(delta);
                sink.send("reasoning", Map.of("delta", delta));
            }

            @Override
            public void onDelta(String delta) {
                content.append(delta);
                sink.send("delta", Map.of("delta", delta));
            }

            @Override
            public void onFinish(ToolStreamResult result) {
                holder[0] = result;
            }
        }, cancelled::get);
        return holder[0] == null ? new ToolStreamResult(null, List.of(), null, null) : holder[0];
    }

    /**
     * 平台提示词 + 智能体自己的提示词。
     * JSON 输出模式与工具调用互斥，因此这里恒为 false（入会话时已排除 jsonOutput=1 的智能体）。
     */
    private ChatOptions optionsOf(CopilotRun run) {
        AiAgent agent = run.agent();
        return new ChatOptions(agent.getModel(), systemPrompt(run), agent.getTemperature(),
                agent.getTopP(), agent.getMaxTokens(),
                agent.getThinking() != null && agent.getThinking() == 1,
                agent.getReasoningEffort(), false);
    }

    private String systemPrompt(CopilotRun run) {
        String platform = PLATFORM_PROMPT.formatted(run.ctx().siteName(), run.ctx().siteId());
        String own = run.agent().getSystemPrompt();
        if (!StringUtils.hasText(own)) {
            return platform;
        }
        // 智能体自己的提示词在前：它决定角色与语气，平台的站点与安全约束在后（更难被忽略）
        return own.trim() + "\n\n" + platform;
    }

    /* ---------------- 工具调用 ---------------- */

    private List<ToolExecutionResult> executeToolCalls(CopilotRun run, List<ToolCall> toolCalls,
                                                       List<CopilotMessage> messages, CopilotSink sink,
                                                       AtomicBoolean cancelled, boolean overLimit) {
        int size = toolCalls.size();
        ToolExecutionResult[] slots = new ToolExecutionResult[size];
        List<Integer> readySlots = new ArrayList<>();
        List<ToolCallRequest> ready = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            ToolCall call = toolCalls.get(i);
            JsonNode args = parseArgs(call);
            ToolSpec spec = registry.find(call.name()).orElse(null);
            emitToolCall(sink, call, spec, args);

            if (overLimit) {
                slots[i] = reject(run, call, spec, "本轮对话已达到 " + MAX_ROUNDS + " 轮工具调用上限，该调用未执行，请回复用户当前进展");
                continue;
            }
            if (cancelled.get()) {
                slots[i] = reject(run, call, spec, "会话已取消，操作未执行");
                continue;
            }
            if (spec == null) {
                slots[i] = reject(run, call, null, "未知工具：" + call.name() + "，请改用已提供的工具");
                continue;
            }
            if (!permission.allowed(spec, run.ctx().perms())) {
                slots[i] = reject(run, call, spec, "当前用户没有权限调用该工具（需要权限：" + spec.permission() + "）");
                continue;
            }
            if (args == null) {
                // docs/ai-copilot.md §7.1.1：拼出来的 JSON 仍可能不合法（模型截断、网关掉包）。
                // 必须兜住：回灌一条 isError 工具结果让模型重试，而不是让整条 SSE 以 500 结束。
                slots[i] = reject(run, call, spec, "参数不是合法 JSON，请重新发起该工具调用并给出完整参数");
                continue;
            }
            if (spec.risk().requiresConfirm()) {
                if (!run.ctx().perms().contains(CONFIRM_PERMISSION)) {
                    slots[i] = reject(run, call, spec, "当前用户没有确认危险操作的权限（需要权限：" + CONFIRM_PERMISSION + "）");
                    continue;
                }
                ChatSessionStore.ConfirmDecision decision = ask(run, spec, call, args, messages, sink);
                if (!decision.allow()) {
                    slots[i] = reject(run, call, spec, "用户拒绝执行该操作：" + decision.reason());
                    continue;
                }
                if (decision.argsOverride() != null) {
                    args = decision.argsOverride();
                }
            }
            readySlots.add(i);
            ready.add(new ToolCallRequest(call.id(), spec, args));
        }

        if (!ready.isEmpty()) {
            List<ToolExecutionResult> executed = executor.executeAll(ready, run.ctx());
            for (int i = 0; i < executed.size(); i++) {
                slots[readySlots.get(i)] = executed.get(i);
            }
        }
        return Arrays.asList(slots);
    }

    /** 危险操作的人工确认闸门：发 confirm 事件后阻塞等待，超时按 DENY 处理。 */
    private ChatSessionStore.ConfirmDecision ask(CopilotRun run, ToolSpec spec, ToolCall call,
                                                 JsonNode args, List<CopilotMessage> messages, CopilotSink sink) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", call.id());
        payload.put("name", spec.name());
        payload.put("title", spec.title());
        payload.put("args", args);
        payload.put("risk", spec.risk().name());
        payload.put("expiresIn", ChatSessionStore.CONFIRM_TIMEOUT_MINUTES * 60);
        if ("cms_publish_run".equals(spec.name())) {
            // 组合风险：AI 可以先改坏模板、再自己发布上线，而用户在"发布全站"的弹窗里看不到模板刚被改坏。
            // 会话内的 cms_site_file_replace 调用记录现成就有，列出来就能挡住这条翻车路径。
            payload.put("sessionChanges", changedTemplates(messages));
        }
        sink.send("confirm", payload);

        CompletableFuture<ChatSessionStore.ConfirmDecision> future =
                store.awaitConfirm(run.sessionId(), call.id());
        try {
            return future.get(ChatSessionStore.CONFIRM_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            return ChatSessionStore.ConfirmDecision.deny(
                    "用户未在 " + ChatSessionStore.CONFIRM_TIMEOUT_MINUTES + " 分钟内确认，操作已取消");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatSessionStore.ConfirmDecision.deny("会话已结束，操作已取消");
        } catch (ExecutionException | CancellationException e) {
            return ChatSessionStore.ConfirmDecision.deny("会话已结束，操作已取消");
        } finally {
            store.clearPending(run.sessionId());
        }
    }

    /** 本会话内改过的模板文件（路径 + 改动行数），用于发布确认弹窗。 */
    private List<Map<String, Object>> changedTemplates(List<CopilotMessage> messages) {
        Map<String, Map<String, Object>> changed = new LinkedHashMap<>();
        for (CopilotMessage message : messages) {
            if (!"assistant".equals(message.role()) || message.toolCalls() == null) {
                continue;
            }
            for (ToolCall call : message.toolCalls()) {
                if (!REPLACE_TOOL.equals(call.name())) {
                    continue;
                }
                JsonNode args = parseJson(call.arguments());
                if (args == null || !args.isObject()) {
                    continue;
                }
                String path = args.path("path").asText("");
                if (path.isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("path", path);
                row.put("newLines", args.path("newText").asText("").split("\n", -1).length);
                row.put("oldLines", args.path("oldText").asText("").split("\n", -1).length);
                changed.put(path, row);
            }
        }
        return new ArrayList<>(changed.values());
    }

    /* ---------------- 事件与落库 ---------------- */

    private void emitToolCall(CopilotSink sink, ToolCall call, ToolSpec spec, JsonNode args) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", call.id());
        payload.put("name", call.name());
        payload.put("title", spec == null ? call.name() : spec.title());
        // 参数非法 JSON 时把原文交出去，前端能看到模型实际发了什么
        payload.put("args", args == null ? call.arguments() : args);
        payload.put("risk", spec == null ? ToolRisk.READ.name() : spec.risk().name());
        sink.send("tool_call", payload);
    }

    private void emitToolResult(CopilotSink sink, ToolExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", result.toolCallId());
        payload.put("ok", result.ok());
        payload.put("summary", result.summary() == null ? result.error() : result.summary());
        payload.put("durationMs", result.durationMs());
        payload.put("siteId", result.siteId());
        payload.put("status", result.status());
        sink.send("tool_result", payload);
    }

    private long saveAssistant(CopilotRun run, String content, String reasoning, List<ToolCall> toolCalls) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(run.sessionId());
        message.setSeq(nextSeq(run.sessionId()));
        message.setRole("assistant");
        message.setContent(content.isEmpty() ? null : content);
        // reasoning_content 完整落库不截断：它是历史回灌能否被服务端接受的关键（§4.5.3）
        message.setReasoningContent(reasoning.isEmpty() ? null : reasoning);
        message.setToolCalls(toolCalls.isEmpty() ? null : writeJson(toolCalls));
        message.setSiteId(run.ctx().siteId());
        messageMapper.insert(message);
        return message.getId() == null ? 0L : message.getId();
    }

    private void saveToolMessage(CopilotRun run, ToolExecutionResult result) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(run.sessionId());
        message.setSeq(nextSeq(run.sessionId()));
        message.setRole("tool");
        message.setContent(result.content());
        // tool_call_id 原样落库，不截断、不重命名、不重新生成
        message.setToolCallId(result.toolCallId());
        message.setToolName(result.name());
        message.setToolArgs(result.content());
        message.setToolStatus(result.status());
        message.setToolDurationMs((int) result.durationMs());
        message.setSiteId(run.ctx().siteId());
        messageMapper.insert(message);
    }

    private int nextSeq(long sessionId) {
        Integer max = messageMapper.selectMaxSeq(sessionId);
        return (max == null ? 0 : max) + 1;
    }

    private void finishSession(long sessionId, int rounds, int toolCalls, int inputTokens, int outputTokens) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        AiChatSession update = new AiChatSession();
        update.setId(sessionId);
        update.setRounds(nz(session.getRounds()) + rounds);
        update.setToolCallCount(nz(session.getToolCallCount()) + toolCalls);
        update.setInputTokens(nz(session.getInputTokens()) + inputTokens);
        update.setOutputTokens(nz(session.getOutputTokens()) + outputTokens);
        update.setStatus(1);
        sessionMapper.updateById(update);
    }

    /* ---------------- 历史与工具 ---------------- */

    /**
     * 从库里重放历史。{@code reasoning_content} 与 {@code tool_call_id} 原样取出，
     * 不做任何截断或加工（§4.5.3、§10.4）。
     */
    private List<CopilotMessage> history(long sessionId) {
        List<AiChatMessage> rows = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getSeq));
        List<CopilotMessage> messages = new ArrayList<>(rows.size());
        for (AiChatMessage row : rows) {
            switch (row.getRole() == null ? "" : row.getRole()) {
                case "user" -> messages.add(CopilotMessage.user(row.getContent()));
                case "assistant" -> messages.add(CopilotMessage.assistant(row.getContent(),
                        row.getReasoningContent(), readToolCalls(row.getToolCalls())));
                case "tool" -> messages.add(CopilotMessage.tool(row.getToolCallId(), row.getContent()));
                default -> {
                    // 未知角色（脏数据）：不喂给模型，避免服务端因消息形状报 400
                }
            }
        }
        return messages;
    }

    private List<ToolCall> readToolCalls(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<ToolCall> calls = objectMapper.readValue(json, new TypeReference<List<ToolCall>>() {
            });
            return calls == null ? List.of() : calls;
        } catch (Exception e) {
            log.warn("会话里的 tool_calls 无法解析，按空处理：{}", e.getMessage());
            return List.of();
        }
    }

    private JsonNode parseArgs(ToolCall call) {
        return parseJson(call.arguments());
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.isBlank()) {
            // 无参工具：模型可能给空串或 "{}"；这里统一当空对象
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 不经过执行器的即时结果：未知工具 / 无权限 / 参数非法 / 被拒绝 / 超轮次。 */
    private ToolExecutionResult reject(CopilotRun run, ToolCall call, ToolSpec spec, String reason) {
        String name = spec == null ? call.name() : spec.name();
        String title = spec == null ? call.name() : spec.title();
        ToolRisk risk = spec == null ? ToolRisk.READ : spec.risk();
        // 被用户拒绝是有明确语义的状态（2），越权是 3，其余按失败（0）处理
        int status = reason.startsWith("用户拒绝") || reason.startsWith("用户未在")
                ? ToolExecutionResult.STATUS_DENIED
                : reason.contains("没有权限") || reason.contains("没有确认危险操作的权限")
                        ? ToolExecutionResult.STATUS_FORBIDDEN
                        : ToolExecutionResult.STATUS_FAIL;
        return new ToolExecutionResult(call.id(), name, title, false, null, reason, reason, 0L,
                run.ctx().siteId(), status, risk, null);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
