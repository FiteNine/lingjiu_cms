package com.lingjiuw.cms.module.ai.copilot.tool;

import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.site.SiteContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 会话级执行上下文（docs/ai-copilot.md §7.3 的"上下文捕获"）。
 *
 * <p>Agent 循环跑在虚拟线程上，那时请求线程早已返回，{@code SiteContext} 与
 * {@code SecurityContextHolder} 两个 ThreadLocal 都是空的。因此在**请求线程上先捕获**
 * LoginUser + perms + 收敛后的 siteId，再带到执行线程上显式恢复。
 *
 * <p><b>不改 {@code MODE_INHERITABLETHREADLOCAL}。</b>那会影响整个应用所有线程池的行为，
 * 是典型的"解决一处、污染全局"。
 *
 * <p>上下文恢复只走 {@link #call} 这一处，别处不许直接 {@code SiteContext.set}。
 */
@Slf4j
public record ToolContext(
        LoginUser user,
        Set<String> perms,
        long siteId,
        String siteName,
        long sessionId,
        long agentId) {

    public static ToolContext of(LoginUser user, long siteId, String siteName, long sessionId, long agentId) {
        Set<String> perms = user.getPerms() == null ? Set.of() : Set.copyOf(user.getPerms());
        return new ToolContext(user, perms, siteId, siteName, sessionId, agentId);
    }

    /** 在目标线程上恢复两个 ThreadLocal，执行 action，最后一定清理。 */
    public <T> T call(Callable<T> action) throws Exception {
        bind();
        try {
            return action.call();
        } finally {
            unbind();
        }
    }

    public void bind() {
        SiteContext.set(siteId);
        List<SimpleGrantedAuthority> authorities = perms.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    public void unbind() {
        SiteContext.clear();
        SecurityContextHolder.clearContext();
    }
}
