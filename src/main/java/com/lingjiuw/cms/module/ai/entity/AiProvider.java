package com.lingjiuw.cms.module.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 服务商：一行 = 一个服务商 × 一种协议（code + protocol 唯一）。
 */
@Data
@TableName("ai_provider")
public class AiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务商显示名，如 DeepSeek */
    private String name;

    /** 服务商标识，如 deepseek */
    private String code;

    /** OPENAI / ANTHROPIC / RESPONSES，取值见 {@code AiProtocol} */
    private String protocol;

    private String baseUrl;

    /** API Key 明文；不参与 JSON 序列化，对外一律走 ProviderVO 的掩码 */
    @JsonIgnore
    private String apiKey;

    /** 1 启用 / 0 停用 */
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
