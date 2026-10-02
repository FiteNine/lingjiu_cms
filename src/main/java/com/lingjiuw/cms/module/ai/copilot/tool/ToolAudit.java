package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.module.system.entity.SysOperLog;
import com.lingjiuw.cms.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具审计：**只有 WRITE / DESTRUCTIVE 工具**额外写一行 {@code sys_oper_log}
 * （docs/ai-copilot.md §3.5）。
 *
 * <p>直接构造 {@link SysOperLog} 写入，**不走 {@code @OperLog} 切面**——切面依赖
 * {@code RequestContextHolder}，而工具跑在非请求线程上，{@code method}/{@code uri}/{@code ip}
 * 会全空。
 *
 * <p>READ 工具不写 {@code sys_oper_log}：否则一次对话能把操作日志刷几十行，
 * 把人工操作的痕迹淹掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolAudit {

    private static final int MAX_PARAMS = 2000;

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    public void record(ToolContext ctx, ToolCallRequest call, ToolExecutionResult result) {
        SysOperLog row = new SysOperLog();
        row.setModule("AI工具");
        row.setAction(call.name());
        row.setMethod("TOOL");
        row.setUri(null);
        row.setIp(null);
        row.setUserId(ctx.user().getId());
        row.setUsername(ctx.user().getUsername());
        row.setStatus(result.ok() ? 1 : 0);
        row.setErrorMsg(truncate(result.error()));
        row.setDurationMs((int) result.durationMs());
        row.setParams(truncate(stringify(call.args())));
        try {
            operLogMapper.insert(row);
        } catch (Exception e) {
            // 审计失败不能影响工具本身的执行结果
            log.warn("AI 工具审计写入失败：{} - {}", call.name(), e.getMessage());
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_PARAMS ? value.substring(0, MAX_PARAMS) : value;
    }
}
