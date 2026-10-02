package com.lingjiuw.cms.module.ai.copilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全站 agent 的一条消息。**追加写，不更新**。
 *
 * <p>刻意没有 {@code deleted}：它是追加日志，删会话时按 {@code session_id} 清理，
 * 因此实体上不加 {@code @TableLogic}（加了会让 MyBatis-Plus 在查询里自动带 {@code deleted = 0}，
 * 而这一列并不存在）。
 *
 * <p>{@code reasoningContent} 与 {@code toolCallId} 是历史回灌能否被服务端接受的关键
 * （docs/ai-copilot.md §10.4），**必须原样存取，不做任何截断或加工**。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** 会话内递增序号，唯一 */
    private Integer seq;

    /** user / assistant / tool */
    private String role;

    private String content;

    private String reasoningContent;

    /** assistant 发起的工具调用，JSON 数组文本 */
    private String toolCalls;

    private String toolCallId;

    private String toolName;

    private String toolArgs;

    /** 1 成功 / 0 失败 / 2 被用户拒绝 / 3 无权限 */
    private Integer toolStatus;

    private Integer toolDurationMs;

    private Long siteId;

    private Integer inputTokens;

    private Integer outputTokens;

    private LocalDateTime createTime;
}
