package com.lingjiuw.cms.module.system.dto;

import com.lingjiuw.cms.module.system.entity.SysOperLog;

import java.time.LocalDateTime;

/**
 * 操作日志对外视图。
 * 不含 params（原始请求参数，可能含密码/API Key/对话内容）与审计内部字段。
 */
public record OperLogVO(
        Long id,
        String module,
        String action,
        String method,
        String uri,
        String ip,
        String username,
        Integer status,
        String errorMsg,
        Integer durationMs,
        LocalDateTime createTime) {

    public static OperLogVO from(SysOperLog log) {
        return new OperLogVO(log.getId(), log.getModule(), log.getAction(), log.getMethod(), log.getUri(),
                log.getIp(), log.getUsername(), log.getStatus(), log.getErrorMsg(), log.getDurationMs(),
                log.getCreateTime());
    }
}
