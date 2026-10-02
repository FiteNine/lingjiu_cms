package com.lingjiuw.cms.module.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能体：绑定服务商 + 模型参数 + 系统提示词。
 */
@Data
@TableName("ai_agent")
public class AiAgent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Long providerId;

    /** 模型名，如 deepseek-flash / deepseek-v4-pro */
    private String model;

    private String systemPrompt;

    /** 0.00 ~ 2.00，思考模式下官方声明不生效 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal temperature;

    /** 仅思考模式生效，官方有效范围 0.95 ~ 1.00 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal topP;

    /** 1 ~ 393216，留空用官方默认（非思考 8K / 思考 64K） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer maxTokens;

    /** 1 开启思考模式（官方默认）/ 0 关闭 */
    private Integer thinking;

    /** low / high / max */
    private String reasoningEffort;

    /** 1 要求返回 JSON；Anthropic 协议无对应参数，忽略 */
    private Integer jsonOutput;

    /** 1 允许调用工具（全站 agent）/ 0 只对话（退化成试聊语义） */
    private Integer toolEnabled;

    /** 逗号分隔的工具组白名单；空 = CONTENT,TEMPLATE_READ,SYSTEM_READ。要能清空，故用 ALWAYS */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String toolScope;

    private Integer status;

    private String remark;

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
