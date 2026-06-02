package com.example.server_springboot.kb.service;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import java.util.List;

public interface KnowledgeConflictResolver {
  List<KnowledgeChunkSearchResponse> resolve(String query, List<KnowledgeChunkSearchResponse> candidates);
}
