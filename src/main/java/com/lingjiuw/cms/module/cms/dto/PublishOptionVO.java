package com.lingjiuw.cms.module.cms.dto;

/**
 * 一个站点发布选项及其类型元数据（static-publish.md §2.7）。
 *
 * <p>表里只有 (site_id, option_code, value)，{@code valueType} 是后台编辑页要的补充信息：
 * {@code bool} / {@code number} / {@code json} / {@code text}，由发布引擎的三份选项清单判定。
 */
public record PublishOptionVO(String optionCode, String value, String valueType) {
}
