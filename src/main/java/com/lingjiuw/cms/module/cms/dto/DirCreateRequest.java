package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 在站点根目录下新建文件夹；parent 为相对 cms.site.root-dir 的路径，留空表示根目录 */
public record DirCreateRequest(
        String parent,
        @NotBlank(message = "文件夹名称不能为空") @Size(max = 64, message = "文件夹名称最长 64 字符") String name) {
}
