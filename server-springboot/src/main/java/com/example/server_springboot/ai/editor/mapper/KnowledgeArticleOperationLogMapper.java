package com.example.server_springboot.ai.editor.mapper;

import com.example.server_springboot.ai.editor.entity.KnowledgeArticleOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeArticleOperationLogMapper {
  int insert(KnowledgeArticleOperationLog log);
}
