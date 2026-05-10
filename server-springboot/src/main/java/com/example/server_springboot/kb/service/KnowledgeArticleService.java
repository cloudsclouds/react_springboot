package com.example.server_springboot.kb.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleDetailResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleListItemResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleVersionResponse;
import com.example.server_springboot.kb.dto.RollbackKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.UpdateKnowledgeArticleRequest;
import java.util.List;

public interface KnowledgeArticleService {
  ApiResponse<List<KnowledgeArticleListItemResponse>> listArticles(Long userId);

  ApiResponse<KnowledgeArticleDetailResponse> getArticle(Long articleId, Long userId);

  ApiResponse<CreateKnowledgeArticleResponse> createArticle(CreateKnowledgeArticleRequest request, Long userId);

  ApiResponse<java.util.Map<String, Object>> updateArticle(Long articleId, UpdateKnowledgeArticleRequest request, Long userId);

  ApiResponse<String> deleteArticle(Long articleId, Long userId);

  ApiResponse<List<KnowledgeArticleVersionResponse>> listVersions(Long articleId, Long userId);

  ApiResponse<java.util.Map<String, Object>> rollbackArticle(Long articleId, RollbackKnowledgeArticleRequest request, Long userId);
}
