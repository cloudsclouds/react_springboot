package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.dto.KnowledgeRetrievalDiagnosticsResponse;
import com.example.server_springboot.kb.dto.KnowledgeRetrievalEvalResponse;
import com.example.server_springboot.kb.entity.KnowledgeArticle;
import com.example.server_springboot.kb.entity.KnowledgeArticleChunk;
import com.example.server_springboot.kb.mapper.KnowledgeArticleChunkMapper;
import com.example.server_springboot.kb.mapper.KnowledgeArticleMapper;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import com.example.server_springboot.kb.service.KnowledgeBM25Service;
import com.example.server_springboot.kb.service.KnowledgeConflictResolver;
import com.example.server_springboot.kb.service.KnowledgeRerankService;
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

  // 默认检索返回条数；当调用方不传或传非法值时使用。
  private static final int DEFAULT_TOP_K = 5;
  // 当原始内容无法解析为结构化文档时，按纯文本兜底切块的最大长度。
  private static final int PLAIN_FALLBACK_MAX_CHUNK_LENGTH = 1400;
  // 目标切块长度，用于尽量把 section 内的内容聚合到更适合检索的粒度。
  private static final int TARGET_CHUNK_LENGTH = 1100;
  // 单个 chunk 过短时的最小阈值，避免产生过碎的语义片段。
  private static final int MIN_CHUNK_LENGTH = 500;
  // 单个 chunk 过长时的上限，超过后会继续拆分。
  private static final int MAX_CHUNK_LENGTH = 1500;
  // 拆分长 chunk 时保留的重叠字符，减少跨边界语义丢失。
  private static final int OVERLAP_CHARS = 120;
  // 混合召回时的扩召倍数，先多召回一些候选再统一重排。
  private static final int HYBRID_RECALL_MULTIPLIER = 4;
  // 向量相似度在混合评分中的权重。
  private static final double VECTOR_WEIGHT = 0.75d;
  // 关键词命中在混合评分中的权重。
  private static final double KEYWORD_WEIGHT = 0.25d;
  // 低于该分数的候选会被视为噪声并过滤。
  private static final double MIN_RELEVANCE_SCORE = 0.18d;
  // 检索诊断时用于判断召回置信度是否偏低的阈值。
  private static final double LOW_CONFIDENCE_THRESHOLD = 0.35d;
  // RRF（Reciprocal Rank Fusion）中的平滑常量。
  private static final int RRF_K = 60;
  // 交叉重排前保留的候选数。
  private static final int RE_RANK_TOP_K = 8;
  // 最终返回前的重排结果数量上限。
  private static final int RE_RANK_FINAL_TOP_K = 5;
  // 查询扩写时最多拆出多少个子查询。
  private static final int QUERY_EXPANSION_LIMIT = 3;
  // Redis 中向量实体的 key 前缀。
  private static final String RAG_VECTOR_KEY_PREFIX = "kb:rag:vector:";
  // Redis 中文章索引集合的 key 前缀。
  private static final String RAG_INDEX_KEY_PREFIX = "kb:rag:index:";

  private final KnowledgeArticleMapper articleMapper;
  private final KnowledgeArticleChunkMapper chunkMapper;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final DashScopeEmbeddingClient dashScopeEmbeddingClient;
  private final KnowledgeBM25Service bm25Service;
  private final KnowledgeRerankService rerankService;
  private final KnowledgeConflictResolver conflictResolver;

  @Override
  public ApiResponse<Map<String, Object>> ingestArticle(Long articleId, Long userId) {
    // 1. 先校验文章归属，避免用户把别人的文章重新入库。
    KnowledgeArticle article = articleMapper.selectById(articleId);
    if (article == null || !Objects.equals(article.getUserId(), userId)) {
      return ApiResponse.error("文章不存在或无权操作");
    }

    // 2. 重新入库前先清理旧 chunk，确保同一文章只保留一份最新切块结果。
    chunkMapper.deleteByArticleId(articleId);
    List<ArticleChunkPiece> pieces = splitContent(article.getContent());
    List<String> embeddingIds = new ArrayList<>();

    // 3. 将文章按语义/结构切分为多个 chunk，并为每个 chunk 生成稳定的 embeddingId。
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
      // 4. 同步把 chunk 的向量、元数据写入 Redis，供后续召回和排序直接使用。
      persistVector(articleId, embeddingId, piece.text(), chunk);
      embeddingIds.add(embeddingId);
    }

    // 5. 返回入库统计信息，便于前端或调用方展示结果。
    Map<String, Object> data = new HashMap<>();
    data.put("articleId", articleId);
    data.put("chunkCount", pieces.size());
    data.put("embeddingIds", embeddingIds);
    return ApiResponse.success("入库成功", data);
  }

  @Override
  public ApiResponse<List<KnowledgeChunkSearchResponse>> searchChunks(Long userId, String query, Long articleId, Integer topK) {
    // 基础检索入口，默认不输出诊断信息，直接复用增强版检索逻辑。
    return searchChunksAdvanced(userId, query, articleId, topK, false);
  }

  @Override
  public ApiResponse<List<KnowledgeChunkSearchResponse>> searchChunksAdvanced(Long userId, String query, Long articleId, Integer topK, boolean useDiagnostics) {
    // 统一处理 topK 默认值，避免调用方传入 null 或非正数时引发异常。
    int limit = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    if (!StringUtils.hasText(query)) {
      return ApiResponse.error("query 不能为空");
    }

    // 先解析可检索的文章范围：指定文章时只查单篇，否则查当前用户的全部可见文章。
    List<Long> targetArticleIds = resolveTargetArticleIds(userId, articleId);
    if (targetArticleIds.isEmpty()) {
      log.info("RAG search no target articles, userId={}, articleId={}, query={}", userId, articleId, query);
      return ApiResponse.success("查询成功", List.of());
    }

    // 召回阶段同时使用向量、关键词和 BM25，尽量扩大候选集合。
    List<KnowledgeChunkSearchResponse> result = new ArrayList<>();
    for (Long targetArticleId : targetArticleIds) {
      result.addAll(searchByArticle(targetArticleId, query, limit));
      result.addAll(bm25Service.search(query, targetArticleId, userId, Math.max(limit, RE_RANK_TOP_K)));
    }

    // 去重、冲突消解、过滤低分候选后，再进行最终重排。
    result = conflictResolver.resolve(query, deduplicateByChunkId(result));
    result.removeIf(item -> item.getScore() == null || item.getScore() < MIN_RELEVANCE_SCORE);
    result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    List<KnowledgeChunkSearchResponse> reranked = rerankService.rerank(query, result, RE_RANK_FINAL_TOP_K);
    return ApiResponse.success("查询成功", reranked.stream().limit(limit).toList());
  }

  @Override
  public ApiResponse<KnowledgeRetrievalDiagnosticsResponse> diagnoseRetrieval(Long userId, String query, Long articleId, Integer topK) {
    // 诊断模式下同样沿用默认 topK，确保与普通检索行为一致。
    int limit = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    // 将原始 query 做一次压缩，便于记录和返回给前端展示。
    String compressed = compressQuery(query);
    // 将 query 拆成多个子查询，分别观察每个子问题的召回质量。
    List<String> subQueries = expandSubQueries(query);
    List<KnowledgeRetrievalEvalResponse> evaluations = new ArrayList<>();
    List<KnowledgeChunkSearchResponse> all = new ArrayList<>();
    for (String q : subQueries) {
      ApiResponse<List<KnowledgeChunkSearchResponse>> response = searchChunksAdvanced(userId, q, articleId, limit, true);
      List<KnowledgeChunkSearchResponse> data = response == null || response.getData() == null ? List.of() : response.getData();
      all.addAll(data);
      // 计算单个子查询的平均得分与置信度，帮助判断是“没查到”还是“查到了但不够好”。
      double avg = data.stream().mapToDouble(item -> item.getScore() == null ? 0d : item.getScore()).average().orElse(0d);
      double confidence = computeConfidence(avg, data.size(), limit);
      evaluations.add(new KnowledgeRetrievalEvalResponse(articleId, q, data.size(), Math.min(limit, data.size()), avg, confidence, data));
    }
    boolean lowConfidence = isLowConfidence(all, limit);
    String refusalReason = lowConfidence ? "检索证据不足，建议补充更具体的关键词或相关文档。" : null;
    return ApiResponse.success("诊断成功", new KnowledgeRetrievalDiagnosticsResponse(query, compressed, subQueries, lowConfidence, refusalReason, evaluations));
  }

  private void persistVector(Long articleId, String embeddingId, String chunkText, KnowledgeArticleChunk chunk) {
    // 计算 chunk 的向量表示，并将向量与元数据一并写入 Redis，便于后续检索阶段快速读取。
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
    // 维护文章级索引集合，后续可以快速枚举文章下所有 chunk key。
    stringRedisTemplate.opsForSet().add(RAG_INDEX_KEY_PREFIX + articleId, key);
  }

  private List<KnowledgeChunkSearchResponse> searchByArticle(Long articleId, String query, int limit) {
    // 先扩大召回范围，再通过后续重排压缩结果，提升召回率。
    int recallLimit = Math.max(limit, limit * HYBRID_RECALL_MULTIPLIER);
    List<String> vectorCandidates = searchVectorKeys(articleId, query, recallLimit);
    List<String> keywordCandidates = searchKeywordKeys(articleId, query, recallLimit);
    List<String> merged = mergeCandidates(vectorCandidates, keywordCandidates);
    List<KnowledgeChunkSearchResponse> result = new ArrayList<>();
    for (String key : merged) {
      String id = key.substring(RAG_VECTOR_KEY_PREFIX.length());
      KnowledgeArticleChunk chunk = chunkMapper.selectByEmbeddingId(id);
      if (chunk == null || !Objects.equals(chunk.getArticleId(), articleId)) {
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
    return result;
  }

  private List<String> mergeCandidates(List<String> vectorCandidates, List<String> keywordCandidates) {
    Map<String, Double> rrfScores = new HashMap<>();
    accumulateRrf(rrfScores, vectorCandidates);
    accumulateRrf(rrfScores, keywordCandidates);
    return rrfScores.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }

  private void accumulateRrf(Map<String, Double> scores, List<String> candidates) {
    for (int i = 0; i < candidates.size(); i++) {
      String key = candidates.get(i);
      scores.merge(key, 1.0d / (RRF_K + i + 1), Double::sum);
    }
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
      double vectorScore = cosine(queryVector, storedVector);
      Object chunkTextObj = stringRedisTemplate.opsForHash().get(key, "chunkText");
      double keyword = chunkTextObj instanceof String chunkText ? keywordScore(query, chunkText) : 0d;
      matches.add(new VectorMatch(key, VECTOR_WEIGHT * vectorScore + KEYWORD_WEIGHT * keyword));
    }
    matches.sort((a, b) -> Double.compare(b.score, a.score));
    return matches.stream().limit(limit).map(m -> m.key).toList();
  }

  private List<String> searchKeywordKeys(Long articleId, String query, int limit) {
    String indexKey = RAG_INDEX_KEY_PREFIX + articleId;
    var redisKeys = stringRedisTemplate.opsForSet().members(indexKey);
    if (redisKeys == null || redisKeys.isEmpty()) {
      return List.of();
    }
    List<VectorMatch> matches = new ArrayList<>();
    for (String key : redisKeys) {
      Object chunkTextObj = stringRedisTemplate.opsForHash().get(key, "chunkText");
      if (!(chunkTextObj instanceof String chunkText) || !StringUtils.hasText(chunkText)) {
        continue;
      }
      double score = keywordScore(query, chunkText);
      matches.add(new VectorMatch(key, score));
    }
    matches.sort((a, b) -> Double.compare(b.score, a.score));
    return matches.stream().limit(limit).map(m -> m.key).toList();
  }

  private List<KnowledgeChunkSearchResponse> reRank(String query, List<KnowledgeChunkSearchResponse> candidates) {
    List<KnowledgeChunkSearchResponse> sorted = new ArrayList<>(candidates);
    sorted.sort((left, right) -> {
      double leftScore = rerankScore(query, left);
      double rightScore = rerankScore(query, right);
      return Double.compare(rightScore, leftScore);
    });
    return sorted;
  }

  private double rerankScore(String query, KnowledgeChunkSearchResponse chunk) {
    double lexical = keywordScore(query, chunk.getChunkText());
    double semantic = similarity(query, chunk.getChunkText());
    double summaryBonus = StringUtils.hasText(chunk.getChunkSummary()) ? keywordScore(query, chunk.getChunkSummary()) * 0.2d : 0d;
    return semantic * 0.7d + lexical * 0.3d + summaryBonus;
  }

  private String compressQuery(String query) {
    if (!StringUtils.hasText(query)) {
      return "";
    }
    String normalized = query.trim().replaceAll("\\s+", " ");
    if (normalized.length() <= 80) {
      return normalized;
    }
    return normalized.substring(0, 80);
  }

  private List<String> expandSubQueries(String query) {
    List<String> subQueries = new ArrayList<>();
    String compressed = compressQuery(query);
    if (!StringUtils.hasText(compressed)) {
      return subQueries;
    }
    subQueries.add(compressed);
    String[] delimiters = compressed.split("[，,。；;？?！!\\n\\r\\t]");
    for (String part : delimiters) {
      String trimmed = part.trim();
      if (trimmed.length() < 4) {
        continue;
      }
      subQueries.add(trimmed);
      if (subQueries.size() >= QUERY_EXPANSION_LIMIT) {
        break;
      }
    }
    return subQueries.stream().distinct().toList();
  }

  private boolean isLowConfidence(List<KnowledgeChunkSearchResponse> candidates, int limit) {
    if (candidates == null || candidates.isEmpty()) {
      return true;
    }
    double avgScore = candidates.stream().mapToDouble(item -> item.getScore() == null ? 0d : item.getScore()).average().orElse(0d);
    return avgScore < LOW_CONFIDENCE_THRESHOLD || candidates.size() < Math.max(1, limit / 2);
  }

  private double computeConfidence(double avgScore, int selectedCount, int limit) {
    double coverage = limit <= 0 ? 0d : Math.min(1d, (double) selectedCount / (double) limit);
    return Math.max(0d, Math.min(1d, avgScore * 0.7d + coverage * 0.3d));
  }

  private List<KnowledgeChunkSearchResponse> deduplicateByChunkId(List<KnowledgeChunkSearchResponse> candidates) {
    Map<Long, KnowledgeChunkSearchResponse> map = new HashMap<>();
    for (KnowledgeChunkSearchResponse candidate : candidates) {
      if (candidate == null || candidate.getChunkId() == null) {
        continue;
      }
      KnowledgeChunkSearchResponse existing = map.get(candidate.getChunkId());
      if (existing == null || (candidate.getScore() != null && candidate.getScore() > (existing.getScore() == null ? 0d : existing.getScore()))) {
        map.put(candidate.getChunkId(), candidate);
      }
    }
    return new ArrayList<>(map.values());
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
