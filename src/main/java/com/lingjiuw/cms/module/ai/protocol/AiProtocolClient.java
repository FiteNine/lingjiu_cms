package com.lingjiuw.cms.module.ai.protocol;

import com.lingjiuw.cms.module.ai.dto.AiMessage;
import com.lingjiuw.cms.module.ai.dto.ChatOptions;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.entity.AiProvider;

import java.util.List;

/**
 * 一种对话协议的适配器。三种协议（OpenAI 兼容 / Anthropic 兼容 / Responses）各一个实现，
 * 接入其它兼容这些协议的服务商只需在「AI服务商」里加数据行，无需改代码。
 */
public interface AiProtocolClient {

    AiProtocol protocol();

    /** 发一次对话请求，返回归一化结果；失败抛 BizException */
    ChatResult chat(AiProvider provider, ChatOptions options, List<AiMessage> messages);
}
