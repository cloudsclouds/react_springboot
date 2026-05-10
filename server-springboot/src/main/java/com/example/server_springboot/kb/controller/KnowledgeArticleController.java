package com.example.server_springboot.kb.controller;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleDetailResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleListItemResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleVersionResponse;
import com.example.server_springboot.kb.dto.RollbackKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.UpdateKnowledgeArticleRequest;
import com.example.server_springboot.kb.service.KnowledgeArticleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb/articles")
@RequiredArgsConstructor
public class KnowledgeArticleController {
  private final KnowledgeArticleService knowledgeArticleService;

  @GetMapping
  public ApiResponse<List<KnowledgeArticleListItemResponse>> listArticles() {
    return knowledgeArticleService.listArticles(UserContext.getUserId());
  }

  @GetMapping("/{articleId}")
  public ApiResponse<KnowledgeArticleDetailResponse> getArticle(@PathVariable Long articleId) {
    return knowledgeArticleService.getArticle(articleId, UserContext.getUserId());
  }

  @PostMapping
  public ApiResponse<CreateKnowledgeArticleResponse> createArticle(@Valid @RequestBody CreateKnowledgeArticleRequest request) {
    return knowledgeArticleService.createArticle(request, UserContext.getUserId());
  }

  @PutMapping("/{articleId}")
  public ApiResponse<Map<String, Object>> updateArticle(@PathVariable Long articleId, @Valid @RequestBody UpdateKnowledgeArticleRequest request) {
    return knowledgeArticleService.updateArticle(articleId, request, UserContext.getUserId());
  }

  @DeleteMapping("/{articleId}")
  public ApiResponse<String> deleteArticle(@PathVariable Long articleId) {
    return knowledgeArticleService.deleteArticle(articleId, UserContext.getUserId());
  }

  @GetMapping("/{articleId}/versions")
  public ApiResponse<List<KnowledgeArticleVersionResponse>> listVersions(@PathVariable Long articleId) {
    return knowledgeArticleService.listVersions(articleId, UserContext.getUserId());
  }

  @PostMapping("/{articleId}/rollback")
  public ApiResponse<Map<String, Object>> rollbackArticle(@PathVariable Long articleId, @Valid @RequestBody RollbackKnowledgeArticleRequest request) {
    return knowledgeArticleService.rollbackArticle(articleId, request, UserContext.getUserId());
  }
}
