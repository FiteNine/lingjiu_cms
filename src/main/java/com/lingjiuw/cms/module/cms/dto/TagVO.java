package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 标签列表项（含内容数）。
 *
 * <p>{@code contentCount} 是计数而不是 id：覆盖全局 Long→String，按数值输出
 * （前端 admin-ui 的 TagItem.contentCount 声明为 number）。
 */
public record TagVO(Long id, String name, String slug,
                    @JsonSerialize(using = LongNumberSerializer.class) Long contentCount) {
}
