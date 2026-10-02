package com.lingjiuw.cms.module.ai.copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.ai.copilot.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /** 会话内当前最大 seq；没有消息时返回 null（调用方按 0 起算）。SQL 见 XML。 */
    Integer selectMaxSeq(@Param("sessionId") Long sessionId);
}
