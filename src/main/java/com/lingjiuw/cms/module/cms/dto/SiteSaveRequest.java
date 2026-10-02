package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增/编辑站点请求；rootDir 为相对 cms.site.root-dir 的目录，不存在时后端自动创建 */
public record SiteSaveRequest(
        @NotBlank(message = "站点名称不能为空") @Size(max = 64, message = "站点名称最长 64 字符") String name,
        @NotBlank(message = "站点标识不能为空")
        @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "站点标识只能用小写字母、数字、下划线或短横线")
        String code,
        @Size(max = 255, message = "站点域名最长 255 字符") String domain,
        @Size(max = 255, message = "站点 Logo 最长 255 字符") String logo,
        @Size(max = 500, message = "站点描述最长 500 字符") String description,
        @Size(max = 255, message = "SEO 关键词最长 255 字符") String keywords,
        @Size(max = 500, message = "SEO 描述最长 500 字符") String seoDescription,
        @NotBlank(message = "站点目录不能为空") @Size(max = 255, message = "站点目录最长 255 字符") String rootDir,
        @Size(max = 64, message = "ICP 备案号最长 64 字符") String icp,
        @Size(max = 32, message = "联系电话最长 32 字符") String contactPhone,
        @Size(max = 128, message = "联系邮箱最长 128 字符") String contactEmail,
        @Pattern(regexp = "https?", message = "站点协议只能是 http 或 https") String protocol,
        @Size(max = 16, message = "站点语言最长 16 字符")
        @Pattern(regexp = "[a-zA-Z]{2}(-[A-Za-z0-9]{2,8})?", message = "站点语言要写成 zh-CN 这样的形式")
        String lang,
        @Size(max = 64, message = "主题名最长 64 字符")
        @Pattern(regexp = "[A-Za-z0-9_-]{0,64}", message = "主题名只能用字母、数字、下划线或短横线")
        String theme,
        @Size(max = 255, message = "默认封面图最长 255 字符") String defaultCover,
        @Size(max = 255, message = "社交分享图最长 255 字符") String ogImage,
        @Size(max = 4000, message = "统计脚本最长 4000 字符")
        String statisticsCode,
        @Min(value = 0, message = "站点状态只能是 0 或 1")
        @Max(value = 1, message = "站点状态只能是 0 或 1")
        Integer status) {
}
