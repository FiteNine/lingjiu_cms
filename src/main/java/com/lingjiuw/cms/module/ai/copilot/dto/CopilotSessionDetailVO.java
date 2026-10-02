package com.lingjiuw.cms.module.ai.copilot.dto;

import java.util.List;

/** 会话详情：会话头 + 全部消息（按 seq 升序），用于回放。 */
public record CopilotSessionDetailVO(CopilotSessionVO session, List<CopilotMessageVO> messages) {
}
