package com.lingjiuw.cms.common.security;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 当前登录用户（从 JWT 解析而来，随认证信息保存在 SecurityContext 中）。
 *
 * <p>Principal 认证完成后不允许再被改写：字段一律 final，roles/perms 在构造时拷贝成不可变集合，
 * 不对外暴露 setter 与可变引用；toString 排除权限明细，避免被打印进日志。
 */
@Getter
@ToString(exclude = {"roles", "perms"})
public class LoginUser {

    private final Long id;
    private final String username;
    private final String nickname;
    private final List<String> roles;
    private final List<String> perms;

    @Builder
    private LoginUser(Long id, String username, String nickname, List<String> roles, List<String> perms) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.roles = copyOf(roles);
        this.perms = copyOf(perms);
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }
}
