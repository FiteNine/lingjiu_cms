package com.lingjiuw.cms.module.cms.dto;

/**
 * 右上角站点切换器的下拉选项。
 *
 * <p>接口登录即可读（不要求 cms:site:list），所以只暴露下拉需要的字段，
 * 不像 {@code CmsSite} 那样带上站点目录的服务器绝对路径；并且只含当前用户可访问的站点。
 */
public record SiteOption(Long id, String name, String code, Integer isDefault) {
}
