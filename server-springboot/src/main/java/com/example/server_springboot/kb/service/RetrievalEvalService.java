package com.example.server_springboot.kb.service;

import com.example.server_springboot.kb.dto.KnowledgeRetrievalDiagnosticsResponse;

public interface RetrievalEvalService {
  KnowledgeRetrievalDiagnosticsResponse diagnose(Long userId, String query, Long articleId, Integer topK);
}
