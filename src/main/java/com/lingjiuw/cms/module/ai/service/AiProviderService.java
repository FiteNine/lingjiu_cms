package com.lingjiuw.cms.module.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.dto.ProviderSaveRequest;
import com.lingjiuw.cms.module.ai.dto.ProviderVO;
import com.lingjiuw.cms.module.ai.entity.AiAgent;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.mapper.AiAgentMapper;
import com.lingjiuw.cms.module.ai.mapper.AiProviderMapper;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderService {

    /** 分页 size 上限：前端分页器最大只用到 100，这里兜住被直接构造的超大 size */
    private static final long MAX_PAGE_SIZE = 200;

    private final AiProviderMapper providerMapper;
    private final AiAgentMapper agentMapper;
    private final AiChatService chatService;

    public PageResult<ProviderVO> page(long page, long size, String keyword, String protocol) {
        Page<AiProvider> result = providerMapper.selectPage(new Page<>(Math.max(page, 1), clampSize(size)),
                Wrappers.<AiProvider>lambdaQuery()
                        .and(StringUtils.hasText(keyword), query -> query
                                .like(AiProvider::getName, keyword).or().like(AiProvider::getCode, keyword))
                        .eq(StringUtils.hasText(protocol), AiProvider::getProtocol, protocol)
                        .orderByAsc(AiProvider::getId));
        return new PageResult<>(result.getRecords().stream().map(ProviderVO::of).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 智能体表单的服务商下拉：停用的也要返回，否则下拉框会显示成裸 id */
    public List<AiProvider> options() {
        return providerMapper.selectList(Wrappers.<AiProvider>lambdaQuery()
                .orderByAsc(AiProvider::getId));
    }

    public void create(ProviderSaveRequest request) {
        String protocol = AiProtocol.of(request.protocol()).name();
        if (exists(request.code(), protocol, null)) {
            throw new BizException("该服务商下已存在同协议配置");
        }
        AiProvider provider = new AiProvider();
        provider.setName(request.name());
        provider.setCode(request.code());
        provider.setProtocol(protocol);
        provider.setBaseUrl(request.baseUrl().trim());
        provider.setApiKey(blankToNull(request.apiKey()));
        provider.setStatus(request.status() == null ? 1 : request.status());
        provider.setRemark(request.remark());
        try {
            providerMapper.insert(provider);
        } catch (DuplicateKeyException e) {
            // uk_ai_provider_code_protocol：并发下先查重可能同时通过，这里兜底成统一的业务异常
            throw new BizException("该服务商下已存在同协议配置");
        }
    }

    public void update(Long id, ProviderSaveRequest request) {
        AiProvider provider = require(id);
        String protocol = AiProtocol.of(request.protocol()).name();
        if (exists(request.code(), protocol, id)) {
            throw new BizException("该服务商下已存在同协议配置");
        }
        provider.setName(request.name());
        provider.setCode(request.code());
        provider.setProtocol(protocol);
        provider.setBaseUrl(request.baseUrl().trim());
        if (StringUtils.hasText(request.apiKey())) {
            provider.setApiKey(request.apiKey().trim());
        }
        provider.setStatus(request.status() == null ? 1 : request.status());
        provider.setRemark(request.remark());
        try {
            providerMapper.updateById(provider);
        } catch (DuplicateKeyException e) {
            // uk_ai_provider_code_protocol：并发下先查重可能同时通过，这里兜底成统一的业务异常
            throw new BizException("该服务商下已存在同协议配置");
        }
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long agents = agentMapper.selectCount(Wrappers.<AiAgent>lambdaQuery().eq(AiAgent::getProviderId, id));
        if (agents != null && agents > 0) {
            throw new BizException("该服务商下存在 " + agents + " 个智能体，不能删除");
        }
        providerMapper.deleteById(id);
    }

    /** 连通性测试：发一条最小对话，用来验证地址 / Key / 协议是否配对 */
    public ChatResult test(Long id, String model) {
        AiProvider provider = require(id);
        return chatService.chat(provider, ChatOptions.probe(model.trim()),
                List.of(new AiMessage("user", "ping")));
    }

    public AiProvider require(Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException("服务商不存在或已被删除");
        }
        return provider;
    }

    private boolean exists(String code, String protocol, Long excludeId) {
        return providerMapper.exists(Wrappers.<AiProvider>lambdaQuery()
                .eq(AiProvider::getCode, code)
                .eq(AiProvider::getProtocol, protocol)
                .ne(excludeId != null, AiProvider::getId, excludeId));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
