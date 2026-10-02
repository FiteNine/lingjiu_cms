package com.lingjiuw.cms.module.ai.copilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全站 agent 会话。
 *
 * <p>{@code siteId} 在会话创建时由 {@code SiteService#resolveSiteId} 收敛后钉死，
 * 会话内的每一次工具执行都按它恢复 {@code SiteContext}——不让 {@code X-Site-Id} 的静默回落
 * 把内容写到别的站点去（docs/ai-copilot.md §3.3）。
 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 取首条用户消息前 50 字，便于会话列表回看 */
    private String title;

    private Long userId;

    /** 会话内钉死的站点 */
    private Long siteId;

    private Long agentId;

    /** 1 进行中 / 0 已结束 */
    private Integer status;

    private Integer rounds;

    private Integer toolCallCount;

    private Long inputTokens;

    private Long outputTokens;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
