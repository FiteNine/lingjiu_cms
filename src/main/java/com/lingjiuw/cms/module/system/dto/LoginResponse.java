package com.lingjiuw.cms.module.system.dto;

/** 登录响应 */
public record LoginResponse(
        String token,
        String tokenType,
        /** 有效期（单位：秒） */
        int expiresIn,
        ProfileVO user) {
}
