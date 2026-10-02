package com.lingjiuw.cms.module.system.dto;

import java.util.List;

/** 当前登录用户信息 */
public record ProfileVO(
        Long id,
        String username,
        String nickname,
        String avatar,
        String email,
        String phone,
        List<String> roles,
        List<String> perms) {
}
