package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘统计。
 *
 * <p>七个统计值是计数而不是 id：覆盖全局 Long→String，按数值输出
 * （前端 admin-ui 的 StatsData 声明为 number，卡片需要数值形态）。
 */
public record StatsVO(
        @JsonSerialize(using = LongNumberSerializer.class) Long contentTotal,
        @JsonSerialize(using = LongNumberSerializer.class) Long contentPublished,
        @JsonSerialize(using = LongNumberSerializer.class) Long contentDraft,
        @JsonSerialize(using = LongNumberSerializer.class) Long categoryTotal,
        @JsonSerialize(using = LongNumberSerializer.class) Long tagTotal,
        @JsonSerialize(using = LongNumberSerializer.class) Long mediaTotal,
        @JsonSerialize(using = LongNumberSerializer.class) Long userTotal,
        List<RecentContent> recentContents) {

    /** 最近内容简要信息 */
    public record RecentContent(Long id, String typeCode, String title, String status,
                                LocalDateTime createTime, String authorName) {
    }
}
