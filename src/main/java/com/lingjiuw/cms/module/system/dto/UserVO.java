package com.lingjiuw.cms.module.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 用户列表项 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        Integer status,
        String remark,
        LocalDateTime createTime,
        List<RoleBrief> roles,
        List<Long> roleIds,
        /** 可切换访问的站点，见 sys_user_site */
        List<Long> siteIds) {
}
