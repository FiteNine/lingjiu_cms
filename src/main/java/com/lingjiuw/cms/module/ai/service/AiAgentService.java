package com.lingjiuw.cms.module.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.dto.AgentSaveRequest;
import com.lingjiuw.cms.module.ai.dto.AgentVO;
import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.entity.AiAgent;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.mapper.AiAgentMapper;
import com.lingjiuw.cms.module.ai.mapper.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private static final Set<String> EFFORTS = Set.of("low", "high", "max");
    private static final String DEFAULT_EFFORT = "high";
    /** 分页 size 上限：前端分页器最大只用到 100，这里兜住被直接构造的超大 size */
    private static final long MAX_PAGE_SIZE = 200;

    private final AiAgentMapper agentMapper;
    private final AiProviderMapper providerMapper;
    private final AiChatService chatService;

    public PageResult<AgentVO> page(long page, long size, String keyword, Long providerId) {
        Page<AiAgent> result = agentMapper.selectPage(new Page<>(Math.max(page, 1), clampSize(size)),
                Wrappers.<AiAgent>lambdaQuery()
                        .and(StringUtils.hasText(keyword), query -> query
                                .like(AiAgent::getName, keyword).or().like(AiAgent::getCode, keyword))
                        .eq(providerId != null, AiAgent::getProviderId, providerId)
                        .orderByDesc(AiAgent::getId));
        Map<Long, AiProvider> providers = providersByIds(result.getRecords());
        return new PageResult<>(
                result.getRecords().stream().map(agent -> AgentVO.of(agent, providers.get(agent.getProviderId()))).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public void create(AgentSaveRequest request) {
        if (agentMapper.exists(Wrappers.<AiAgent>lambdaQuery().eq(AiAgent::getCode, request.code()))) {
            throw new BizException("智能体标识已存在");
        }
        AiAgent agent = new AiAgent();
        applyRequest(agent, request);
        agentMapper.insert(agent);
    }

    public void update(Long id, AgentSaveRequest request) {
        AiAgent agent = require(id);
        if (agentMapper.exists(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getCode, request.code())
                .ne(AiAgent::getId, id))) {
            throw new BizException("智能体标识已存在");
        }
        applyRequest(agent, request);
        agentMapper.updateById(agent);
    }

    public void delete(Long id) {
        require(id);
        agentMapper.deleteById(id);
    }

    /** 试聊：把前端传来的对话历史连同智能体的系统提示词与模型参数发一次请求 */
    public ChatResult chat(Long id, List<AiMessage> messages) {
        AiAgent agent = require(id);
        if (agent.getStatus() == null || agent.getStatus() != 1) {
            throw new BizException("智能体「" + agent.getName() + "」已停用");
        }
        AiProvider provider = providerMapper.selectById(agent.getProviderId());
        if (provider == null) {
            throw new BizException("智能体绑定的服务商不存在或已被删除");
        }
        return chatService.chat(provider, ChatOptions.of(agent), messages);
    }

    public AiAgent require(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException("智能体不存在或已被删除");
        }
        return agent;
    }

    private void applyRequest(AiAgent agent, AgentSaveRequest request) {
        if (providerMapper.selectById(request.providerId()) == null) {
            throw new BizException("服务商不存在或已被删除");
        }
        String effort = StringUtils.hasText(request.reasoningEffort()) ? request.reasoningEffort() : DEFAULT_EFFORT;
        if (!EFFORTS.contains(effort)) {
            throw new BizException("思考强度只能是 low / high / max");
        }
        agent.setName(request.name());
        agent.setCode(request.code());
        agent.setProviderId(request.providerId());
        agent.setModel(request.model());
        agent.setSystemPrompt(request.systemPrompt());
        agent.setTemperature(request.temperature());
        agent.setTopP(request.topP());
        agent.setMaxTokens(request.maxTokens());
        agent.setThinking(request.thinking() == null ? 1 : request.thinking());
        agent.setReasoningEffort(effort);
        agent.setJsonOutput(request.jsonOutput() == null ? 0 : request.jsonOutput());
        agent.setToolEnabled(request.toolEnabled() == null ? 1 : request.toolEnabled());
        agent.setToolScope(StringUtils.hasText(request.toolScope()) ? request.toolScope().trim() : null);
        agent.setStatus(request.status() == null ? 1 : request.status());
        agent.setRemark(request.remark());
    }

    private Map<Long, AiProvider> providersByIds(List<AiAgent> agents) {
        Set<Long> ids = agents.stream().map(AiAgent::getProviderId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return providerMapper.selectList(Wrappers.<AiProvider>lambdaQuery().in(AiProvider::getId, ids)).stream()
                .collect(Collectors.toMap(AiProvider::getId, Function.identity()));
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
