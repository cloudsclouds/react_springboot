package com.example.server_springboot.kb.service;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import java.util.List;

public interface KnowledgeRerankService {
  List<KnowledgeChunkSearchResponse> rerank(String query, List<KnowledgeChunkSearchResponse> candidates, int limit);
}
