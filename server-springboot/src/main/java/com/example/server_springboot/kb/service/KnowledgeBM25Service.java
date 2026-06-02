package com.example.server_springboot.kb.service;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import java.util.List;

public interface KnowledgeBM25Service {
  List<KnowledgeChunkSearchResponse> search(String query, Long articleId, Long userId, int limit);
}
