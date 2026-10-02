package com.lingjiuw.cms.common.security;

import com.lingjiuw.cms.common.exception.BizException;

/**
 * 获取当前登录用户的工具类。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户；未登录返回 null（站点解析这类匿名请求也要走的路径用） */
    public static LoginUser userOrNull() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof LoginUser user ? user : null;
    }

    public static LoginUser user() {
        LoginUser user = userOrNull();
        if (user == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return user;
    }

    public static Long userId() {
        return user().getId();
    }

    public static String username() {
        return user().getUsername();
    }
}
