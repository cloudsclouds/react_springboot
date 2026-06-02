package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.entity.KnowledgeArticle;
import com.example.server_springboot.kb.entity.KnowledgeArticleChunk;
import com.example.server_springboot.kb.mapper.KnowledgeArticleChunkMapper;
import com.example.server_springboot.kb.mapper.KnowledgeArticleMapper;
import com.example.server_springboot.kb.service.KnowledgeBM25Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeBM25ServiceImpl implements KnowledgeBM25Service {
  /**
   * BM25 的核心参数：
   * K1 控制词频增长的饱和速度，数值越大，词频对结果的影响越明显。
   */
  private static final double K1 = 1.2d;
  /**
   * BM25 的核心参数：
   * B 用于调节文档长度归一化的强度，越接近 1 越强调长文档惩罚。
   */
  private static final double B = 0.75d;
  /**
   * 分词后的最小有效长度，过滤掉过短、噪声较大的 token。
   */
  private static final int MIN_TOKEN_LENGTH = 2;

  private final KnowledgeArticleMapper articleMapper;
  private final KnowledgeArticleChunkMapper chunkMapper;

  @Override
  public List<KnowledgeChunkSearchResponse> search(String query, Long articleId, Long userId, int limit) {
    // 查询内容为空或返回数量非法时，直接返回空结果，避免后续无意义计算。
    if (!StringUtils.hasText(query) || limit <= 0) {
      return List.of();
    }

    // 先解析本次检索需要限定到哪些文章范围：
    // - 指定 articleId 时，只查这篇文章；
    // - 未指定时，默认查当前用户下的所有未删除文章。
    List<Long> targetArticleIds = resolveTargetArticleIds(articleId, userId);
    if (targetArticleIds.isEmpty()) {
      return List.of();
    }

    // 按文章拉取 chunk，并合并成一个候选集合。
    List<KnowledgeArticleChunk> chunks = new ArrayList<>();
    for (Long targetArticleId : targetArticleIds) {
      List<KnowledgeArticleChunk> articleChunks = chunkMapper.selectByArticleIdOrderByChunkIndex(targetArticleId);
      if (articleChunks != null) {
        chunks.addAll(articleChunks);
      }
    }
    if (chunks.isEmpty()) {
      return List.of();
    }

    // 将用户查询标准化为 token 列表，后续只对这些关键词进行 BM25 计算。
    List<String> queryTokens = tokenize(query);
    if (queryTokens.isEmpty()) {
      return List.of();
    }

    // 统计每个 token 的文档频次（document frequency），以及每个 chunk 的长度，
    // 这些都是 BM25 公式计算所需的基础数据。
    Map<String, Integer> docFreq = new HashMap<>();
    Map<Long, Integer> docLength = new HashMap<>();
    int totalLength = 0;
    for (KnowledgeArticleChunk chunk : chunks) {
      String text = fullText(chunk);
      List<String> tokens = tokenize(text);
      docLength.put(chunk.getId(), tokens.size());
      totalLength += tokens.size();

      // 一个 chunk 中同一个 token 只需要计入一次文档频次，因此这里先去重再累加。
      LinkedHashSet<String> uniqueTokens = new LinkedHashSet<>(tokens);
      for (String token : uniqueTokens) {
        docFreq.merge(token, 1, Integer::sum);
      }
    }

    // 计算所有候选 chunk 的平均长度，供 BM25 做长度归一化。
    double avgDocLength = chunks.isEmpty() ? 0d : (double) totalLength / (double) chunks.size();
    List<KnowledgeChunkSearchResponse> results = new ArrayList<>();
    for (KnowledgeArticleChunk chunk : chunks) {
      String text = fullText(chunk);

      // 对每个 chunk 逐个计算 BM25 分数，分数越高表示与查询越相关。
      double score = bm25(queryTokens, text, chunks.size(), avgDocLength, docFreq, docLength.getOrDefault(chunk.getId(), 0));
      results.add(new KnowledgeChunkSearchResponse(
          chunk.getId(),
          chunk.getArticleId(),
          chunk.getChunkIndex(),
          chunk.getChunkText(),
          chunk.getChunkSummary(),
          chunk.getEmbeddingId(),
          score));
    }

    // BM25 只保留有意义的匹配结果，避免返回 0 分噪声项。
    results.removeIf(item -> item.getScore() == null || item.getScore() <= 0d);
    // 按相关性分数从高到低排序，方便上层直接取前 N 条。
    results.sort(Comparator.comparingDouble(KnowledgeChunkSearchResponse::getScore).reversed());
    return results.stream().limit(limit).toList();
  }

  /**
   * 解析检索目标文章 ID。
   *
   * <p>优先级：
   * 1. 传入 articleId 时，仅允许当前用户访问该文章；
   * 2. 未传入 articleId 时，查询当前用户下的全部可检索文章。
   */
  private List<Long> resolveTargetArticleIds(Long articleId, Long userId) {
    if (articleId != null) {
      KnowledgeArticle article = articleMapper.selectById(articleId);
      // 文章不存在、归属不匹配、或文章处于删除/禁用状态时，都不允许继续检索。
      if (article == null || !Objects.equals(article.getUserId(), userId) || Objects.equals(article.getStatus(), 1)) {
        return List.of();
      }
      return List.of(articleId);
    }

    // 未指定文章时，返回当前用户所有未删除文章的 ID。
    return articleMapper.selectByUserId(userId).stream()
        .filter(article -> !Objects.equals(article.getStatus(), 1))
        .map(KnowledgeArticle::getId)
        .toList();
  }

  /**
   * 计算单个 chunk 相对于查询词的 BM25 相关性得分。
   *
   * <p>这里使用的是标准 BM25 公式：
   * - tf：词项在当前 chunk 中出现的频率；
   * - df：词项在全部候选 chunk 中出现的文档数；
   * - docLength：当前 chunk 的长度；
   * - avgDocLength：候选 chunk 的平均长度。
   */
  private double bm25(List<String> queryTokens, String text, int docCount, double avgDocLength, Map<String, Integer> docFreq, int docLength) {
    if (!StringUtils.hasText(text) || docCount <= 0 || avgDocLength <= 0d) {
      return 0d;
    }

    // 统计当前 chunk 内每个 token 的出现次数，后续直接按词频累加 BM25 分数。
    List<String> docTokens = tokenize(text);
    Map<String, Integer> termFreq = new HashMap<>();
    for (String token : docTokens) {
      termFreq.merge(token, 1, Integer::sum);
    }

    double score = 0d;
    for (String token : queryTokens) {
      Integer tf = termFreq.get(token);
      Integer df = docFreq.get(token);
      if (tf == null || df == null || df <= 0) {
        continue;
      }

      // idf 越大，说明该词越“稀有”，对区分结果的贡献越高。
      double idf = Math.log(1d + ((docCount - df + 0.5d) / (df + 0.5d)));
      double numerator = tf * (K1 + 1d);
      // 长文本会被适当归一化，避免仅凭内容更长就天然获得更高分。
      double denominator = tf + K1 * (1d - B + B * (docLength / avgDocLength));
      score += idf * (numerator / denominator);
    }
    return score;
  }

  /**
   * 将输入文本标准化为 token 列表。
   *
   * <p>处理流程：
   * 1. 全部转小写，统一英文大小写差异；
   * 2. 将非字母数字字符替换为空格；
   * 3. 按空白切分；
   * 4. 过滤过短 token，减少噪声。
   */
  private List<String> tokenize(String value) {
    if (!StringUtils.hasText(value)) {
      return List.of();
    }
    String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    if (!StringUtils.hasText(normalized)) {
      return List.of();
    }
    String[] parts = normalized.split("\\s+");
    List<String> tokens = new ArrayList<>();
    for (String part : parts) {
      if (part.length() >= MIN_TOKEN_LENGTH) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  /**
   * 拼接 chunk 的可检索正文。
   *
   * <p>这里优先把摘要和正文一起参与 BM25 评分，
   * 这样即使正文较长，也能利用摘要提升召回效果。
   */
  private String fullText(KnowledgeArticleChunk chunk) {
    if (chunk == null) {
      return "";
    }
    return (StringUtils.hasText(chunk.getChunkSummary()) ? chunk.getChunkSummary() + " " : "") + (chunk.getChunkText() == null ? "" : chunk.getChunkText());
  }
}
