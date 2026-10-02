package com.lingjiuw.cms.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.module.system.entity.SysOperLog;
import com.lingjiuw.cms.module.system.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

/**
 * 操作日志切面：记录请求参数、耗时、成功/失败。日志写入失败不影响业务。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_LEN = 2000;

    /** 客户端可伪造 X-Forwarded-For，取值只在 IP 字面量的长度内采信（sys_oper_log.ip 是 varchar(64)） */
    private static final int MAX_IP_LENGTH = 45;

    /** 日志中的敏感字段脱敏：字段名里含 password/secret/token/apiKey（大小写不敏感）时值一律打码 */
    private static final Pattern SENSITIVE_FIELD =
            Pattern.compile("(\"[^\"]*(?:password|secret|token|apiKey)[^\"]*\"\\s*:\\s*)"
                            + "(?:\"[^\"]*\"|\\{[^{}]*\\}|\\[[^\\[\\]]*\\]|[^,}\\]]+)",
                    Pattern.CASE_INSENSITIVE);

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLog record = new SysOperLog();
        record.setModule(operLog.module());
        record.setAction(operLog.action());
        record.setParams(truncate(dumpArgs(joinPoint.getArgs())));

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            record.setMethod(request.getMethod());
            record.setUri(request.getRequestURI());
            record.setIp(resolveIp(request));
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            record.setUserId(user.getId());
            record.setUsername(user.getUsername());
        }

        try {
            Object result = joinPoint.proceed();
            record.setStatus(1);
            return result;
        } catch (Throwable e) {
            record.setStatus(0);
            record.setErrorMsg(truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            throw e;
        } finally {
            record.setDurationMs((int) (System.currentTimeMillis() - start));
            try {
                operLogMapper.insert(record);
            } catch (Exception e) {
                log.warn("操作日志写入失败: {}", e.getMessage());
            }
        }
    }

    private String dumpArgs(Object[] args) {
        StringBuilder sb = new StringBuilder("[");
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile || arg instanceof MultipartFile[]) {
                continue;
            }
            try {
                sb.append(objectMapper.writeValueAsString(arg));
            } catch (Exception e) {
                sb.append(String.valueOf(arg));
            }
            sb.append(", ");
        }
        // 用 *** 顶掉整个值（不能把匹配到的值再拼回去，那样明文还会留在日志里）
        return SENSITIVE_FIELD.matcher(sb.append("]")).replaceAll("$1\"***\"");
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            // 该头由客户端提供：长度明显不是一个 IP 的值不采信，避免伪造值污染日志或写爆 ip 列
            if (first.length() <= MAX_IP_LENGTH) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_LEN ? value.substring(0, MAX_LEN) : value;
    }
}
