package com.example.server_springboot.kb.mapper;

import com.example.server_springboot.kb.entity.KnowledgeArticleChunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeArticleChunkMapper {
  int insert(KnowledgeArticleChunk chunk);

  int deleteByArticleId(@Param("articleId") Long articleId);

  List<KnowledgeArticleChunk> selectByArticleIdOrderByChunkIndex(@Param("articleId") Long articleId);

  KnowledgeArticleChunk selectByEmbeddingId(@Param("embeddingId") String embeddingId);
}
