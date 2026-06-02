package com.example.server_springboot.ai.memory.mapper;

import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryRecordMapper {
  int insert(MemoryRecord record);

  int update(MemoryRecord record);

  List<MemoryRecord> selectByUserId(@Param("userId") Long userId);

  List<MemoryRecord> searchByUserId(@Param("userId") Long userId,
                                    @Param("keyword") String keyword,
                                    @Param("limit") Integer limit);

  List<MemoryRecord> selectRecentByConversationId(@Param("conversationId") Long conversationId,
                                                  @Param("limit") Integer limit);

  MemoryRecord selectByUserIdAndContent(@Param("userId") Long userId, @Param("content") String content);
}
