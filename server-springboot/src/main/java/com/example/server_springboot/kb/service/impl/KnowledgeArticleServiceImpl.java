package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleDetailResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleListItemResponse;
import com.example.server_springboot.kb.dto.KnowledgeArticleVersionResponse;
import com.example.server_springboot.kb.dto.RollbackKnowledgeArticleRequest;
import com.example.server_springboot.kb.dto.UpdateKnowledgeArticleRequest;
import com.example.server_springboot.kb.entity.KnowledgeArticle;
import com.example.server_springboot.kb.entity.KnowledgeArticleVersion;
import com.example.server_springboot.kb.mapper.KnowledgeArticleMapper;
import com.example.server_springboot.kb.mapper.KnowledgeArticleVersionMapper;
import com.example.server_springboot.kb.service.KnowledgeArticleService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {
  private final KnowledgeArticleMapper articleMapper;
  private final KnowledgeArticleVersionMapper versionMapper;

  @Override
  public ApiResponse<List<KnowledgeArticleListItemResponse>> listArticles(Long userId) {
    List<KnowledgeArticleListItemResponse> data = articleMapper.selectByUserId(userId).stream()
        .map(article -> new KnowledgeArticleListItemResponse(article.getId(), article.getTitle(), article.getSummary(), article.getUpdatedAt(), article.getStatus()))
        .collect(Collectors.toList());
    return ApiResponse.success("查询成功", data);
  }

  @Override
  public ApiResponse<KnowledgeArticleDetailResponse> getArticle(Long articleId, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !userId.equals(article.getUserId())) {
      return ApiResponse.error("文章不存在或无权访问");
    }
    return ApiResponse.success("查询成功", new KnowledgeArticleDetailResponse(article.getId(), article.getTitle(), article.getSummary(), parseContent(article.getContent()), article.getUpdatedAt()));
  }

  @Override
  public ApiResponse<CreateKnowledgeArticleResponse> createArticle(CreateKnowledgeArticleRequest request, Long userId) {
    KnowledgeArticle article = new KnowledgeArticle();
    article.setUserId(userId);
    article.setTitle(request.getTitle().trim());
    article.setSummary(request.getSummary());
    article.setContent(stringifyContent(request.getContent()));
    article.setStatus(0);
    article.setCreatedAt(LocalDateTime.now());
    article.setUpdatedAt(LocalDateTime.now());
    articleMapper.insert(article);
    return ApiResponse.success("创建成功", new CreateKnowledgeArticleResponse(article.getId(), article.getTitle()));
  }

  @Override
  public ApiResponse<Map<String, Object>> updateArticle(Long articleId, UpdateKnowledgeArticleRequest request, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !userId.equals(article.getUserId())) {
      return ApiResponse.error("文章不存在或无权编辑");
    }
    String newTitle = request.getTitle().trim();
    String newSummary = request.getSummary();
    String newContent = stringifyContent(request.getContent());
    boolean changed = hasChanges(article, newTitle, newSummary, newContent);

    Integer versionNo = getLatestVersionNo(articleId);
    if (changed) {
      KnowledgeArticleVersion version = new KnowledgeArticleVersion();
      version.setArticleId(articleId);
      version.setVersionNo(versionNo + 1);
      version.setSnapshot(article.getContent());
      version.setSource(request.getSaveSource() == null ? "manual" : request.getSaveSource());
      version.setCreatedBy(userId);
      version.setCreatedAt(LocalDateTime.now());
      versionMapper.insert(version);
      versionNo = version.getVersionNo();

      article.setTitle(newTitle);
      article.setSummary(newSummary);
      article.setContent(newContent);
      article.setUpdatedAt(LocalDateTime.now());
      articleMapper.update(article);
    }

    Map<String, Object> data = new HashMap<>();
    data.put("articleId", articleId);
    data.put("versionNo", versionNo);
    data.put("changed", changed);
    return ApiResponse.success(changed ? "保存成功" : "内容未变化", data);
  }

  @Override
  public ApiResponse<String> deleteArticle(Long articleId, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !userId.equals(article.getUserId())) {
      return ApiResponse.error("文章不存在或无权删除");
    }
    article.setStatus(1);
    article.setUpdatedAt(LocalDateTime.now());
    articleMapper.update(article);
    return ApiResponse.success("删除成功", null);
  }

  @Override
  public ApiResponse<List<KnowledgeArticleVersionResponse>> listVersions(Long articleId, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !userId.equals(article.getUserId())) {
      return ApiResponse.error("文章不存在或无权访问");
    }
    List<KnowledgeArticleVersionResponse> data = versionMapper.selectByArticleId(articleId).stream()
        .map(v -> new KnowledgeArticleVersionResponse(v.getVersionNo(), v.getSource(), v.getCreatedAt()))
        .collect(Collectors.toList());
    return ApiResponse.success("查询成功", data);
  }

  @Override
  public ApiResponse<Map<String, Object>> rollbackArticle(Long articleId, RollbackKnowledgeArticleRequest request, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !userId.equals(article.getUserId())) {
      return ApiResponse.error("文章不存在或无权回滚");
    }
    KnowledgeArticleVersion version = versionMapper.selectByArticleIdAndVersionNo(articleId, request.getVersionNo());
    if (version == null) {
      return ApiResponse.error("版本不存在");
    }
    article.setContent(version.getSnapshot());
    article.setUpdatedAt(LocalDateTime.now());
    articleMapper.update(article);
    Map<String, Object> data = new HashMap<>();
    data.put("articleId", articleId);
    data.put("versionNo", request.getVersionNo());
    return ApiResponse.success("回滚成功", data);
  }

  private Integer getLatestVersionNo(Long articleId) {
    return versionMapper.selectByArticleId(articleId).stream()
        .map(KnowledgeArticleVersion::getVersionNo)
        .max(Integer::compareTo)
        .orElse(0);
  }

  private boolean hasChanges(KnowledgeArticle article, String newTitle, String newSummary, String newContent) {
    return !safeEquals(article.getTitle(), newTitle)
        || !safeEquals(article.getSummary(), newSummary)
        || !safeEquals(article.getContent(), newContent);
  }

  private boolean safeEquals(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }

  private String stringifyContent(JsonNode content) {
    return content == null ? null : content.toString();
  }

  private JsonNode parseContent(String content) {
    if (content == null || content.isBlank()) {
      return null;
    }
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
    } catch (Exception ex) {
      throw new IllegalArgumentException("文章内容格式不合法");
    }
  }
}
