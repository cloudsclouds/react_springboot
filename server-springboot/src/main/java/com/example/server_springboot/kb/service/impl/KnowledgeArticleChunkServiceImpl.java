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
import java.util.List;
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
  private static final int PLAIN_FALLBACK_MAX_CHUNK_LENGTH = 1400;
  private static final int TARGET_CHUNK_LENGTH = 1100;
  private static final int MIN_CHUNK_LENGTH = 500;
  private static final int MAX_CHUNK_LENGTH = 1500;
  private static final int OVERLAP_CHARS = 120;
  private static final int HYBRID_RECALL_MULTIPLIER = 4;
  private static final double VECTOR_WEIGHT = 0.75d;
  private static final double KEYWORD_WEIGHT = 0.25d;
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
    if (!StringUtils.hasText(query)) {
      return ApiResponse.error("query 不能为空");
    }

    List<Long> targetArticleIds = resolveTargetArticleIds(userId, articleId);
    if (targetArticleIds.isEmpty()) {
      log.info("RAG search no target articles, userId={}, articleId={}, query={}", userId, articleId, query);
      return ApiResponse.success("查询成功", List.of());
    }

    int recallLimit = Math.max(limit, limit * HYBRID_RECALL_MULTIPLIER);

    List<KnowledgeChunkSearchResponse> result = new ArrayList<>();
    for (Long targetArticleId : targetArticleIds) {
      List<String> candidates = searchVectorKeys(targetArticleId, query, recallLimit);
      if (candidates.isEmpty()) {
        log.info("RAG search empty candidates, articleId={}, query={}", targetArticleId, query);
        continue;
      }

      for (String key : candidates) {
        String id = key.substring(RAG_VECTOR_KEY_PREFIX.length());
        KnowledgeArticleChunk chunk = chunkMapper.selectByEmbeddingId(id);
        if (chunk == null) {
          log.info("RAG chunk missing in mysql, articleId={}, embeddingId={}", targetArticleId, id);
          continue;
        }
        if (!Objects.equals(chunk.getArticleId(), targetArticleId)) {
          continue;
        }
        double vectorScore = similarity(query, chunk.getChunkText());
        double keywordScore = keywordScore(query, chunk.getChunkText());
        double hybridScore = VECTOR_WEIGHT * vectorScore + KEYWORD_WEIGHT * keywordScore;
        result.add(new KnowledgeChunkSearchResponse(
            chunk.getId(),
            chunk.getArticleId(),
            chunk.getChunkIndex(),
            chunk.getChunkText(),
            chunk.getChunkSummary(),
            chunk.getEmbeddingId(),
            hybridScore));
      }
    }

    result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    return ApiResponse.success("查询成功", result.stream().limit(limit).toList());
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
      matches.add(new VectorMatch(key, cosine(queryVector, storedVector)));
    }
    matches.sort((a, b) -> Double.compare(b.score, a.score));
    return matches.stream().limit(limit).map(m -> m.key).toList();
  }

  private List<Long> resolveTargetArticleIds(Long userId, Long articleId) {
    if (articleId != null) {
      KnowledgeArticle article = articleMapper.selectById(articleId);
      if (article == null || !Objects.equals(article.getUserId(), userId) || Objects.equals(article.getStatus(), 1)) {
        return List.of();
      }
      return List.of(articleId);
    }

    return articleMapper.selectByUserId(userId).stream()
        .filter(article -> !Objects.equals(article.getStatus(), 1))
        .map(KnowledgeArticle::getId)
        .toList();
  }

  private List<ArticleChunkPiece> splitContent(String content) {
    List<ArticleChunkPiece> pieces = new ArrayList<>();
    if (!StringUtils.hasText(content)) {
      return pieces;
    }

    try {
      JsonNode root = objectMapper.readTree(content);
      List<Section> sections = new ArrayList<>();
      walkNodes(root.path("content"), sections, new HeadingState());
      for (Section section : sections) {
        pieces.addAll(chunkSection(section));
      }
    } catch (Exception ex) {
      log.warn("split tiptap content failed, fallback to plain text: {}", ex.getMessage());
    }

    if (!pieces.isEmpty()) {
      return pieces;
    }

    String plain = stripJsonToText(content);
    if (!StringUtils.hasText(plain)) {
      return pieces;
    }
    for (int i = 0; i < plain.length(); i += PLAIN_FALLBACK_MAX_CHUNK_LENGTH) {
      String slice = plain.substring(i, Math.min(i + PLAIN_FALLBACK_MAX_CHUNK_LENGTH, plain.length()));
      pieces.add(new ArticleChunkPiece(slice, summarizeText(slice)));
    }
    return pieces;
  }

  private void walkNodes(JsonNode nodes, List<Section> sections, HeadingState headingState) {
    if (nodes == null || !nodes.isArray()) {
      return;
    }
    for (JsonNode node : nodes) {
      String type = node.path("type").asText("");
      if ("heading".equals(type)) {
        int level = node.path("attrs").path("level").asInt(1);
        String heading = extractText(node).trim();
        if (StringUtils.hasText(heading)) {
          headingState.update(level, heading);
          sections.add(new Section(headingState.path(), level));
        }
        continue;
      }

      String text = normalizeNodeText(node);
      if (!StringUtils.hasText(text)) {
        continue;
      }
      if (sections.isEmpty()) {
        sections.add(new Section("未命名章节", 1));
      }
      sections.get(sections.size() - 1).blocks.add(text);
    }
  }

  private List<ArticleChunkPiece> chunkSection(Section section) {
    List<ArticleChunkPiece> result = new ArrayList<>();
    if (section.blocks.isEmpty()) {
      return result;
    }

    StringBuilder window = new StringBuilder();
    for (String block : section.blocks) {
      if (window.length() > 0) {
        window.append("\n\n");
      }
      window.append(block);

      if (window.length() >= TARGET_CHUNK_LENGTH) {
        emitByBoundary(result, section, window.toString());
        window.setLength(0);
      }
    }

    if (window.length() > 0) {
      emitByBoundary(result, section, window.toString());
    }
    return result;
  }

  private void emitByBoundary(List<ArticleChunkPiece> out, Section section, String candidate) {
    String text = candidate.trim();
    while (text.length() > MAX_CHUNK_LENGTH) {
      int splitAt = findSplitPoint(text, MAX_CHUNK_LENGTH);
      String head = text.substring(0, splitAt).trim();
      if (StringUtils.hasText(head)) {
        out.add(toChunk(section, head));
      }
      int nextStart = Math.max(0, splitAt - OVERLAP_CHARS);
      text = text.substring(nextStart).trim();
    }

    if (text.length() >= MIN_CHUNK_LENGTH || out.isEmpty()) {
      out.add(toChunk(section, text));
    } else if (!out.isEmpty()) {
      ArticleChunkPiece prev = out.remove(out.size() - 1);
      String mergedBody = bodyWithoutSectionPrefix(prev.text()) + "\n\n" + text;
      out.add(toChunk(section, mergedBody));
    }
  }

  private ArticleChunkPiece toChunk(Section section, String body) {
    String prefixed = "[Section] " + section.path + "\n" + body.trim();
    return new ArticleChunkPiece(prefixed, summarizeText(body));
  }

  private int findSplitPoint(String text, int preferredMax) {
    int end = Math.min(preferredMax, text.length());
    String head = text.substring(0, end);
    int idx = Math.max(
        Math.max(head.lastIndexOf('。'), head.lastIndexOf('！')),
        Math.max(head.lastIndexOf('？'), head.lastIndexOf('\n')));
    if (idx >= MIN_CHUNK_LENGTH) {
      return idx + 1;
    }
    idx = Math.max(head.lastIndexOf('；'), head.lastIndexOf('，'));
    if (idx >= MIN_CHUNK_LENGTH) {
      return idx + 1;
    }
    return end;
  }

  private String bodyWithoutSectionPrefix(String text) {
    if (!StringUtils.hasText(text)) {
      return "";
    }
    int idx = text.indexOf('\n');
    if (idx < 0 || idx + 1 >= text.length()) {
      return text;
    }
    return text.substring(idx + 1);
  }

  private String normalizeNodeText(JsonNode node) {
    String type = node.path("type").asText("");
    String text = extractText(node).replaceAll("\\s+", " ").trim();
    if (!StringUtils.hasText(text)) {
      return "";
    }
    return switch (type) {
      case "bulletList", "orderedList", "list" -> "- " + text;
      case "codeBlock" -> "```\n" + text + "\n```";
      case "blockquote" -> "> " + text;
      default -> text;
    };
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

  private double keywordScore(String query, String text) {
    if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
      return 0d;
    }
    String normalizedText = normalizeForKeyword(text);
    if (!StringUtils.hasText(normalizedText)) {
      return 0d;
    }

    List<String> tokens = tokenizeQuery(query);
    if (tokens.isEmpty()) {
      return 0d;
    }

    int hitCount = 0;
    for (String token : tokens) {
      if (normalizedText.contains(token)) {
        hitCount++;
      }
    }
    return (double) hitCount / (double) tokens.size();
  }

  private List<String> tokenizeQuery(String query) {
    List<String> tokens = new ArrayList<>();
    String[] parts = normalizeForKeyword(query).split("\\s+");
    for (String part : parts) {
      if (part.length() >= 2) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  private String normalizeForKeyword(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value
        .toLowerCase()
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
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

  private record VectorMatch(String key, double score) {}
  private record ArticleChunkPiece(String text, String summary) {}

  private static class Section {
    private final String path;
    private final int level;
    private final List<String> blocks = new ArrayList<>();

    Section(String path, int level) {
      this.path = path;
      this.level = level;
    }
  }

  private static class HeadingState {
    private final Map<Integer, String> levels = new HashMap<>();

    void update(int level, String title) {
      levels.put(level, title);
      levels.keySet().removeIf(k -> k > level);
    }

    String path() {
      List<Integer> ordered = levels.keySet().stream().sorted().toList();
      StringBuilder builder = new StringBuilder();
      for (Integer lv : ordered) {
        if (builder.length() > 0) {
          builder.append(" > ");
        }
        builder.append("H").append(lv).append(":").append(levels.get(lv));
      }
      return builder.toString();
    }
  }
}
