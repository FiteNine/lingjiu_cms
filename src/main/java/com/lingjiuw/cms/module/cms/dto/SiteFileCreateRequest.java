package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 在当前站点目录下新建文件或文件夹；parent 为相对站点目录的路径，留空表示站点目录本身 */
public record SiteFileCreateRequest(
        String parent,
        @NotBlank(message = "名称不能为空") @Size(max = 64, message = "名称最长 64 字符") String name) {
}
