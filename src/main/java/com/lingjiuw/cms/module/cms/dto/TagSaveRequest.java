package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增/编辑标签请求。
 *
 * <p>{@code slug} 有站点内的部分唯一索引（{@code uk_cms_tag_slug(site_id, slug) where deleted = 0}），
 * 列长 {@code varchar(64)}：长度与形态在这里就卡住，别留给数据库报唯一冲突 / 超长。
 */
public record TagSaveRequest(
        @NotBlank(message = "标签名称不能为空") @Size(max = 64, message = "标签名称最长 64 字符") String name,
        @Size(max = 64, message = "标签 slug 长度不能超过 64 个字符")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug 只能包含小写字母、数字和连字符")
        String slug) {
}
