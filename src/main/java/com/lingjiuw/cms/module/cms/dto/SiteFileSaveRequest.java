package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 保存站点目录下的文本文件；path 相对站点目录 */
public record SiteFileSaveRequest(
        @NotBlank(message = "请选择要保存的文件") String path,
        @NotNull(message = "文件内容不能为空") String content) {
}
