package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.entity.KnowledgeArticle;
import com.example.server_springboot.kb.entity.KnowledgeArticleChunk;
import com.example.server_springboot.kb.mapper.KnowledgeArticleChunkMapper;
import com.example.server_springboot.kb.mapper.KnowledgeArticleMapper;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleChunkServiceImpl implements KnowledgeArticleChunkService {
  private static final Logger log = LoggerFactory.getLogger(KnowledgeArticleChunkServiceImpl.class);
  private static final int DEFAULT_TOP_K = 5;
  private static final int VECTOR_DIMENSION = 1024;
  private static final int MIN_CHUNK_LENGTH = 800;
  private static final int MAX_CHUNK_LENGTH = 1600;
  private static final String RAG_VECTOR_KEY_PREFIX = "kb:rag:vector:";
  private static final String RAG_INDEX_KEY_PREFIX = "kb:rag:index:";

  private final KnowledgeArticleMapper articleMapper;
  private final KnowledgeArticleChunkMapper chunkMapper;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final DashScopeEmbeddingClient dashScopeEmbeddingClient;

  @Override
  public ApiResponse<Map<String, Object>> ingestArticle(Long articleId, Long userId) {
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !Objects.equals(article.getUserId(), userId)) {
      return ApiResponse.error("文章不存在或无权操作");
    }

    chunkMapper.deleteByArticleId(articleId);
    List<ArticleChunkPiece> pieces = splitContent(article.getContent());
    List<String> embeddingIds = new ArrayList<>();

    for (int i = 0; i < pieces.size(); i++) {
      ArticleChunkPiece piece = pieces.get(i);
      String embeddingId = buildEmbeddingId(articleId, i, piece.text());
      KnowledgeArticleChunk chunk = new KnowledgeArticleChunk();
      chunk.setArticleId(articleId);
      chunk.setChunkIndex(i + 1);
      chunk.setChunkText(piece.text());
      chunk.setChunkSummary(piece.summary());
      chunk.setEmbeddingId(embeddingId);
      chunk.setCreatedAt(LocalDateTime.now());
      chunkMapper.insert(chunk);
      persistVector(articleId, embeddingId, piece.text(), chunk);
      embeddingIds.add(embeddingId);
    }

    Map<String, Object> data = new HashMap<>();
    data.put("articleId", articleId);
    data.put("chunkCount", pieces.size());
    data.put("embeddingIds", embeddingIds);
    return ApiResponse.success("入库成功", data);
  }

  @Override
  public ApiResponse<List<KnowledgeChunkSearchResponse>> searchChunks(Long userId, String query, Long articleId, Integer topK) {
    int limit = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    if (articleId == null) {
      return ApiResponse.error("articleId 不能为空");
    }
    List<String> candidates = searchVectorKeys(articleId, query, limit);
    List<KnowledgeChunkSearchResponse> result = new ArrayList<>();
    if (candidates.isEmpty()) {
      log.info("RAG search empty candidates, articleId={}, query={}", articleId, query);
      return ApiResponse.success("查询成功", result);
    }

    for (String key : candidates) {
      String id = key.substring(RAG_VECTOR_KEY_PREFIX.length());
      KnowledgeArticleChunk chunk = chunkMapper.selectByEmbeddingId(id);
      if (chunk == null) {
        log.info("RAG chunk missing in mysql, articleId={}, embeddingId={}", articleId, id);
        continue;
      }
      double score = similarity(query, chunk.getChunkText());
      log.info("RAG candidate score, articleId={}, chunkId={}, chunkIndex={}, embeddingId={}, score={}",
          articleId, chunk.getId(), chunk.getChunkIndex(), chunk.getEmbeddingId(), score);
      result.add(new KnowledgeChunkSearchResponse(
          chunk.getId(),
          chunk.getArticleId(),
          chunk.getChunkIndex(),
          chunk.getChunkText(),
          chunk.getChunkSummary(),
          chunk.getEmbeddingId(),
          score));
    }
    result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    List<KnowledgeChunkSearchResponse> topResults = result.stream().limit(limit).toList();
    log.info("RAG search result size={}, articleId={}, query={}", topResults.size(), articleId, query);
    return ApiResponse.success("查询成功", topResults);
  }

  private void persistVector(Long articleId, String embeddingId, String chunkText, KnowledgeArticleChunk chunk) {
    byte[] vector = dashScopeEmbeddingClient.embed(chunkText);
    String key = RAG_VECTOR_KEY_PREFIX + embeddingId;
    Map<String, String> payload = new HashMap<>();
    payload.put("articleId", String.valueOf(articleId));
    payload.put("chunkId", String.valueOf(chunk.getId()));
    payload.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
    payload.put("chunkText", chunkText);
    payload.put("chunkSummary", chunk.getChunkSummary() == null ? "" : chunk.getChunkSummary());
    payload.put("vector", Base64.getEncoder().encodeToString(vector));
    stringRedisTemplate.opsForHash().putAll(key, payload);
    stringRedisTemplate.opsForSet().add(RAG_INDEX_KEY_PREFIX + articleId, key);
  }

  private List<String> searchVectorKeys(Long articleId, String query, int limit) {
    byte[] queryVector = dashScopeEmbeddingClient.embed(query);
    String indexKey = RAG_INDEX_KEY_PREFIX + articleId;
    var redisKeys = stringRedisTemplate.opsForSet().members(indexKey);
    List<VectorMatch> matches = new ArrayList<>();
    if (redisKeys == null || redisKeys.isEmpty()) {
      return List.of();
    }
    for (String key : redisKeys) {
      Object encoded = stringRedisTemplate.opsForHash().get(key, "vector");
      if (!(encoded instanceof String vectorBase64) || vectorBase64.isBlank()) {
        continue;
      }
      byte[] storedVector = Base64.getDecoder().decode(vectorBase64);
      double score = cosine(queryVector, storedVector);
      matches.add(new VectorMatch(key, score));
    }
    matches.sort((a, b) -> Double.compare(b.score, a.score));
    return matches.stream().limit(limit).map(m -> m.key).toList();
  }

  private double cosine(byte[] left, byte[] right) {
    int dims = Math.min(left.length, right.length) / Double.BYTES;
    if (dims == 0) {
      return 0d;
    }
    double dot = 0d;
    double normA = 0d;
    double normB = 0d;
    for (int i = 0; i < dims; i++) {
      int offset = i * Double.BYTES;
      double av = java.nio.ByteBuffer.wrap(left, offset, Double.BYTES).getDouble();
      double bv = java.nio.ByteBuffer.wrap(right, offset, Double.BYTES).getDouble();
      dot += av * bv;
      normA += av * av;
      normB += bv * bv;
    }
    if (normA == 0d || normB == 0d) {
      return 0d;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  private List<ArticleChunkPiece> splitContent(String content) {
    List<ArticleChunkPiece> pieces = new ArrayList<>();
    if (!StringUtils.hasText(content)) {
      return pieces;
    }
    try {
      JsonNode root = objectMapper.readTree(content);
      JsonNode nodes = root.path("content");
      if (nodes.isArray()) {
        ArticleChunkBuffer buffer = new ArticleChunkBuffer();
        String currentHeading = null;
        for (JsonNode node : nodes) {
          String type = node.path("type").asText("");
          if ("heading".equals(type)) {
            flushBuffer(pieces, buffer, currentHeading);
            currentHeading = extractText(node).trim();
            continue;
          }
          if ("paragraph".equals(type)) {
            String text = extractText(node).trim();
            if (StringUtils.hasText(text)) {
              buffer.append(composeText(currentHeading, text));
            }
            continue;
          }
          if ("bulletList".equals(type) || "orderedList".equals(type) || "list".equals(type)) {
            String listText = extractText(node).trim();
            if (StringUtils.hasText(listText)) {
              buffer.append(composeText(currentHeading, listText));
            }
            continue;
          }
          String fallback = extractText(node).trim();
          if (StringUtils.hasText(fallback)) {
            buffer.append(composeText(currentHeading, fallback));
          }
        }
        flushBuffer(pieces, buffer, currentHeading);
      }
    } catch (Exception ex) {
      // fallback to plain text chunks
    }

    if (pieces.isEmpty()) {
      String plain = stripJsonToText(content);
      if (StringUtils.hasText(plain)) {
        for (int i = 0; i < plain.length(); i += MAX_CHUNK_LENGTH) {
          String slice = plain.substring(i, Math.min(i + MAX_CHUNK_LENGTH, plain.length()));
          pieces.add(new ArticleChunkPiece(slice, summarizeText(slice)));
        }
      }
    }
    return pieces;
  }

  private void flushBuffer(List<ArticleChunkPiece> pieces, ArticleChunkBuffer buffer, String heading) {
    if (buffer == null || !StringUtils.hasText(buffer.text())) {
      return;
    }
    String merged = composeText(heading, buffer.text().trim());
    pieces.add(new ArticleChunkPiece(merged, summarizeText(merged)));
    buffer.clear();
  }

  private String composeText(String heading, String text) {
    return StringUtils.hasText(heading) ? heading + "\n" + text : text;
  }

  private String extractText(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    if (node.has("text")) {
      return node.path("text").asText("");
    }
    if (node.has("content") && node.path("content").isArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonNode child : node.path("content")) {
        String text = extractText(child);
        if (StringUtils.hasText(text)) {
          if (builder.length() > 0) {
            builder.append(' ');
          }
          builder.append(text);
        }
      }
      return builder.toString();
    }
    if (node.has("items") && node.path("items").isArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonNode child : node.path("items")) {
        String text = extractText(child);
        if (StringUtils.hasText(text)) {
          if (builder.length() > 0) {
            builder.append(' ');
          }
          builder.append(text);
        }
      }
      return builder.toString();
    }
    return node.asText("");
  }

  private String stripJsonToText(String content) {
    try {
      JsonNode root = objectMapper.readTree(content);
      return extractText(root);
    } catch (Exception ex) {
      return content;
    }
  }

  private String summarizeText(String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
  }

  private String buildEmbeddingId(Long articleId, int index, String chunkText) {
    return articleId + "-" + (index + 1) + "-" + hash(chunkText);
  }

  private String hash(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed).substring(0, 16);
    } catch (Exception ex) {
      return Integer.toHexString(text.hashCode());
    }
  }

  private double similarity(String query, String text) {
    return cosine(dashScopeEmbeddingClient.embed(query), dashScopeEmbeddingClient.embed(text));
  }

  private record VectorMatch(String key, double score) {}
  private record ArticleChunkPiece(String text, String summary) {}

  private static class ArticleChunkBuffer {
    private final StringBuilder builder = new StringBuilder();

    void append(String text) {
      if (!StringUtils.hasText(text)) {
        return;
      }
      if (builder.length() > 0) {
        builder.append("\n\n");
      }
      builder.append(text.trim());
    }

    String text() {
      return builder.toString();
    }

    void clear() {
      builder.setLength(0);
    }
  }
}
