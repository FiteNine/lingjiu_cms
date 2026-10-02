package com.lingjiuw.cms.module.cms.dto;

/** 站点目录下一个文件的内容：文本给 text，图片给 dataUrl，其余类型两者都为 null */
public record SiteFileContent(String path, String name, long size, String kind, String text, String dataUrl) {
}
