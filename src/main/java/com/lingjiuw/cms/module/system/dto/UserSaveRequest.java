package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增/编辑用户请求。编辑时 password 为空表示不修改密码。
 * roleIds / siteIds 为 null 表示不修改对应分配（siteIds 是用户可切换访问的站点，见 sys_user_site）。
 */
public record UserSaveRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64") String username,
        String password,
        @Size(max = 64, message = "昵称长度不能超过64") String nickname,
        @Size(max = 128, message = "邮箱长度不能超过128")
        @Email(message = "邮箱格式不正确") String email,
        @Size(max = 32, message = "手机号长度不能超过32") String phone,
        Integer status,
        @Size(max = 255, message = "备注过长") String remark,
        List<Long> roleIds,
        List<Long> siteIds) {
}
