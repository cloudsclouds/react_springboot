package com.example.server_springboot.kb.controller;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb/rag")
@RequiredArgsConstructor
public class KnowledgeRagController {
  private final KnowledgeArticleChunkService chunkService;

  @PostMapping("/articles/{articleId}/ingest")
  public ApiResponse<Map<String, Object>> ingest(@PathVariable Long articleId) {
    return chunkService.ingestArticle(articleId, UserContext.getUserId());
  }

  @GetMapping("/search")
  public ApiResponse<List<KnowledgeChunkSearchResponse>> search(
      @RequestParam String query,
      @RequestParam(required = false) Long articleId,
      @RequestParam(required = false, defaultValue = "5") Integer topK) {
    return chunkService.searchChunks(UserContext.getUserId(), query, articleId, topK);
  }
}
