package com.example.server_springboot.kb.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import java.util.List;
import java.util.Map;

public interface KnowledgeArticleChunkService {
  ApiResponse<Map<String, Object>> ingestArticle(Long articleId, Long userId);

  ApiResponse<List<KnowledgeChunkSearchResponse>> searchChunks(Long userId, String query, Long articleId, Integer topK);
}
