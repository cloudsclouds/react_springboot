package com.example.server_springboot.kb.mapper;

import com.example.server_springboot.kb.entity.KnowledgeArticle;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeArticleMapper {
  int insert(KnowledgeArticle article);

  int update(KnowledgeArticle article);

  KnowledgeArticle selectById(@Param("id") Long id);

  List<KnowledgeArticle> selectByUserId(@Param("userId") Long userId);
}
