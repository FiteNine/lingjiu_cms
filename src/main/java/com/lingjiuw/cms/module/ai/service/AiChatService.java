package com.lingjiuw.cms.module.ai.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.entity.AiProvider;
import com.lingjiuw.cms.module.ai.protocol.AiProtocol;
import com.lingjiuw.cms.module.ai.protocol.AiProtocolClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按服务商配置的协议分发到对应适配器；新增协议只需新增一个 {@link AiProtocolClient} 实现。
 */
@Service
public class AiChatService {

    private final Map<String, AiProtocolClient> clients;

    public AiChatService(List<AiProtocolClient> clients) {
        this.clients = clients.stream().collect(Collectors.toMap(
                client -> client.protocol().name(),
                Function.identity(),
                (existing, duplicate) -> {
                    throw new BizException("协议「" + existing.protocol().name() + "」存在多个客户端实现");
                }));
    }

    public ChatResult chat(AiProvider provider, ChatOptions options, List<AiMessage> messages) {
        AiProtocol protocol = AiProtocol.of(provider.getProtocol());
        AiProtocolClient client = clients.get(protocol.name());
        if (client == null) {
            throw new BizException("协议「" + protocol.name() + "」未注册对应的客户端实现");
        }
        return client.chat(provider, options, messages);
    }
}
