package com.example.server_springboot.ai.mapper;

import com.example.server_springboot.ai.entity.AiConversationMessage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiConversationMessageMapper {
  int insert(AiConversationMessage message);

  int updateById(AiConversationMessage message);

  List<AiConversationMessage> selectByConversationId(@Param("conversationId") Long conversationId);
}
