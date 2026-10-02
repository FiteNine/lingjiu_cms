package com.lingjiuw.cms.module.ai.copilot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.module.ai.copilot.dto.AgentOptionVO;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotChatRequest;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotMessageVO;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotSessionDetailVO;
import com.lingjiuw.cms.module.ai.copilot.dto.CopilotSessionVO;
import com.lingjiuw.cms.module.ai.copilot.entity.AiChatMessage;
import com.lingjiuw.cms.module.ai.copilot.entity.AiChatSession;
import com.lingjiuw.cms.module.ai.copilot.mapper.AiChatMessageMapper;
import com.lingjiuw.cms.module.ai.copilot.mapper.AiChatSessionMapper;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolContext;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolPermission;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRegistry;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.ai.entity.AiAgent;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.mapper.AiAgentMapper;
import com.lingjiuw.cms.module.ai.mapper.AiProviderMapper;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全站 agent 的会话与智能体可选性（docs/ai-copilot.md §3.3、§4.5.2、§5.3）。
 *
 * <p>这里承担两件必须放在服务端做的事：
 * <ol>
 *   <li><b>智能体可选性硬校验</b>——前端过滤不是安全边界，协议不是 OPENAI、开了 JSON 输出、
 *       关掉工具开关的智能体一律拒绝建会话；</li>
 *   <li><b>站点钉死</b>——会话创建时把收敛后的 siteId 写进 {@code ai_chat_session.site_id}，
 *       会话内所有工具都按它执行，切站点 = 新会话。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CopilotService {

    private static final String TITLE_SUFFIX = "…";
    private static final int TITLE_LENGTH = 50;
    private static final long MAX_PAGE_SIZE = 200;

    private final AiAgentMapper agentMapper;
    private final AiProviderMapper providerMapper;
    private final CmsSiteMapper siteMapper;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ToolRegistry toolRegistry;
    private final ToolPermission permission;
    private final SiteService siteService;

    /** 一次对话准备好的一切：会话、智能体、服务商、本轮暴露的工具、执行上下文。 */
    public record PreparedChat(long sessionId, AiAgent agent, AiProvider provider,
                               List<ToolSpec> tools, ToolContext ctx) {
    }

    /**
     * 全站 agent 的智能体下拉：只列**能用工具**的智能体。
     *
     * <p>三条排除规则：{@code protocol != OPENAI}（一期没有对应实现）、
     * {@code jsonOutput == 1}（JSON 输出模式与工具调用互斥）、
     * {@code toolEnabled == 0}（该智能体本身就不允许调工具，列出来只会是个死路）。
     */
    public List<AgentOptionVO> agentOptions() {
        List<AiAgent> agents = agentMapper.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getStatus, 1)
                .orderByDesc(AiAgent::getId));
        if (agents.isEmpty()) {
            return List.of();
        }
        java.util.Set<Long> providerIds = agents.stream().map(AiAgent::getProviderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AiProvider> providers = providerIds.isEmpty() ? Map.of()
                : providerMapper.selectBatchIds(providerIds).stream()
                        .collect(Collectors.toMap(AiProvider::getId, Function.identity()));
        List<AgentOptionVO> options = new ArrayList<>();
        for (AiAgent agent : agents) {
            AiProvider provider = providers.get(agent.getProviderId());
            if (provider == null || !AiProtocol.OPENAI.name().equals(normalize(provider.getProtocol()))) {
                continue;
            }
            if (agent.getJsonOutput() != null && agent.getJsonOutput() == 1) {
                continue;
            }
            if (agent.getToolEnabled() != null && agent.getToolEnabled() == 0) {
                continue;
            }
            options.add(AgentOptionVO.of(agent, AiProtocol.OPENAI.name()));
        }
        return options;
    }

    /** 请求线程上完成全部校验与会话落库；失败抛 BizException（由 SSE 的 error 事件回传）。 */
    public PreparedChat prepare(CopilotChatRequest request, LoginUser user, long siteId) {
        if (request.agentId() == null) {
            throw new BizException("请选择智能体");
        }
        if (!StringUtils.hasText(request.message())) {
            throw new BizException("消息不能为空");
        }
        AiAgent agent = requireAgent(request.agentId());
        AiProvider provider = requireUsableProvider(agent);
        AiChatSession session = resolveSession(request, user, siteId, agent);
        String siteName = requireSiteName(session.getSiteId());

        ToolContext ctx = ToolContext.of(user, session.getSiteId(), siteName, session.getId(), agent.getId());
        // 提示词里只注入当前用户实际有权限的工具：既省 token，也减少模型"试错式"调用没权限的工具
        List<ToolSpec> tools = toolRegistry.forScope(agent.getToolScope()).stream()
                .filter(spec -> permission.allowed(spec, ctx.perms()))
                .toList();
        insertUserMessage(session, ctx.siteId(), request.message());
        return new PreparedChat(session.getId(), agent, provider, tools, ctx);
    }

    public PageResult<CopilotSessionVO> sessions(long page, long size, Long userId) {
        Page<AiChatSession> result = sessionMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)),
                Wrappers.<AiChatSession>lambdaQuery()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getId));
        Map<Long, String> siteNames = siteNames(result.getRecords().stream()
                .map(AiChatSession::getSiteId).collect(Collectors.toSet()));
        Map<Long, String> agentNames = agentNames(result.getRecords().stream()
                .map(AiChatSession::getAgentId).collect(Collectors.toSet()));
        return new PageResult<>(result.getRecords().stream()
                .map(session -> CopilotSessionVO.of(session, siteNames.get(session.getSiteId()),
                        agentNames.get(session.getAgentId())))
                .toList(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CopilotSessionDetailVO sessionDetail(Long id, Long userId) {
        AiChatSession session = sessionMapper.selectById(id);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BizException("会话不存在或不属于当前用户");
        }
        List<CopilotMessageVO> messages = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getSessionId, id)
                        .orderByAsc(AiChatMessage::getSeq)).stream()
                .map(CopilotMessageVO::of)
                .toList();
        return new CopilotSessionDetailVO(CopilotSessionVO.of(session,
                siteNames(java.util.Set.of(session.getSiteId())).get(session.getSiteId()),
                agentNames(java.util.Set.of(session.getAgentId())).get(session.getAgentId())), messages);
    }

    /** 当前请求站点收敛后的 id（与站点解析、越权检查走同一条路）。 */
    public long resolveSite(long requested) {
        return siteService.resolveSiteId(requested);
    }

    public String requireSiteName(long siteId) {
        CmsSite site = siteMapper.selectById(siteId);
        return site == null ? "" : site.getName();
    }

    /* ---------------- 内部 ---------------- */

    private AiAgent requireAgent(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException("智能体不存在或已被删除");
        }
        if (agent.getStatus() == null || agent.getStatus() != 1) {
            throw new BizException("智能体「" + agent.getName() + "」已停用");
        }
        if (agent.getToolEnabled() != null && agent.getToolEnabled() == 0) {
            throw new BizException("智能体「" + agent.getName()
                    + "」未开启工具调用，请到 AI管理→智能体 打开「允许调用工具」");
        }
        return agent;
    }

    /** 一期只支持 OPENAI 协议的工具调用；JSON 输出与工具调用互斥。 */
    private AiProvider requireUsableProvider(AiAgent agent) {
        AiProvider provider = providerMapper.selectById(agent.getProviderId());
        if (provider == null) {
            throw new BizException("智能体绑定的服务商不存在或已被删除");
        }
        AiProtocol protocol = AiProtocol.of(provider.getProtocol());
        if (protocol != AiProtocol.OPENAI) {
            throw new BizException("协议「" + protocol.name()
                    + "」暂不支持工具调用，请在「AI服务商」里改用 OPENAI 协议的配置");
        }
        if (agent.getJsonOutput() != null && agent.getJsonOutput() == 1) {
            throw new BizException("智能体「" + agent.getName()
                    + "」开启了 JSON 输出，与工具调用互斥，请在「智能体」里关闭 JSON 输出");
        }
        return provider;
    }

    private AiChatSession resolveSession(CopilotChatRequest request, LoginUser user, long siteId, AiAgent agent) {
        if (request.sessionId() == null) {
            AiChatSession session = new AiChatSession();
            session.setTitle(title(request.message()));
            session.setUserId(user.getId());
            session.setSiteId(siteId);
            session.setAgentId(agent.getId());
            session.setStatus(1);
            sessionMapper.insert(session);
            return session;
        }
        AiChatSession session = sessionMapper.selectById(request.sessionId());
        if (session == null || !Objects.equals(session.getUserId(), user.getId())) {
            throw new BizException("会话不存在或不属于当前用户");
        }
        if (!Objects.equals(session.getSiteId(), siteId)) {
            throw new BizException("该会话属于另一个站点，请新建会话");
        }
        if (!Objects.equals(session.getAgentId(), agent.getId())) {
            throw new BizException("会话所属智能体与请求不一致，请新建会话");
        }
        return session;
    }

    private void insertUserMessage(AiChatSession session, long siteId, String message) {
        Integer max = messageMapper.selectMaxSeq(session.getId());
        AiChatMessage row = new AiChatMessage();
        row.setSessionId(session.getId());
        row.setSeq((max == null ? 0 : max) + 1);
        row.setRole("user");
        row.setContent(message);
        row.setSiteId(siteId);
        messageMapper.insert(row);
    }

    private Map<Long, String> siteNames(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return siteMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CmsSite::getId, CmsSite::getName));
    }

    private Map<Long, String> agentNames(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return agentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AiAgent::getId, AiAgent::getName));
    }

    /** 会话标题：首条用户消息前 50 字（换行折成空格）。 */
    private static String title(String message) {
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= TITLE_LENGTH ? flat : flat.substring(0, TITLE_LENGTH) + TITLE_SUFFIX;
    }

    private static String normalize(String protocol) {
        return protocol == null ? "" : protocol.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
