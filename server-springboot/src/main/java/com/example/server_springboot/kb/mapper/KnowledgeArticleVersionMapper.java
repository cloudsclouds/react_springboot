package com.example.server_springboot.kb.mapper;

import com.example.server_springboot.kb.entity.KnowledgeArticleVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeArticleVersionMapper {
  int insert(KnowledgeArticleVersion version);

  List<KnowledgeArticleVersion> selectByArticleId(@Param("articleId") Long articleId);

  KnowledgeArticleVersion selectByArticleIdAndVersionNo(@Param("articleId") Long articleId, @Param("versionNo") Integer versionNo);
}
