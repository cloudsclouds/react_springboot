package com.example.server_springboot.ai.mapper;

import com.example.server_springboot.ai.entity.AiConversation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiConversationMapper {
  int insert(AiConversation conversation);

  AiConversation selectById(@Param("id") Long id);

  List<AiConversation> selectByUserId(@Param("userId") Long userId);
}
