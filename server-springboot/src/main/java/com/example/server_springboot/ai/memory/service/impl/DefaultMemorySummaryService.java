package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.service.MemorySummaryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认记忆摘要服务。
 *
 * <p>该服务负责把一次对话中已有的摘要内容与新消息进行滚动合并，
 * 生成一个结构化、可持续更新的记忆摘要。
 *
 * <p>摘要格式采用固定分区的方式组织，便于后续检索、展示和二次处理，
 * 例如：目标、约束、决策、未决问题等字段。
 */
@Service
public class DefaultMemorySummaryService implements MemorySummaryService {

  /**
   * 更新滚动摘要。
   *
   * <p>处理逻辑：
   * <ol>
   *   <li>先从历史摘要中提取已有的结构化字段。</li>
   *   <li>再把旧摘要与新消息合并，形成“新增信息”。</li>
   *   <li>最后拼装出带有 conversationId 和 userId 的统一摘要文本。</li>
   * </ol>
   *
   * @param conversationId 当前会话 ID，用于标识摘要归属
   * @param previousSummary 之前已经积累的摘要内容
   * @param newMessages 本轮新增消息内容
   * @param userId 用户 ID，用于区分不同用户的记忆上下文
   * @return 更新后的滚动摘要文本
   */
  @Override
  public String updateRollingSummary(Long conversationId, String previousSummary, String newMessages, Long userId) {
    Map<String, String> sections = new LinkedHashMap<>();
    sections.put("目标", extractLine(previousSummary, "目标"));
    sections.put("约束", extractLine(previousSummary, "约束"));
    sections.put("决策", extractLine(previousSummary, "决策"));
    sections.put("未决问题", extractLine(previousSummary, "未决问题"));

    String merged = buildMergedSummary(previousSummary, newMessages);
    if (StringUtils.hasText(merged)) {
      sections.put("新增信息", merged);
    }

    StringBuilder builder = new StringBuilder();
    builder.append("[conversationId=").append(conversationId == null ? "" : conversationId).append(", userId=").append(userId == null ? "" : userId).append("]\n");
    sections.forEach((key, value) -> {
      builder.append(key).append(": ").append(StringUtils.hasText(value) ? value : "").append("\n");
    });
    return builder.toString().trim();
  }

  /**
   * 合并历史摘要和新消息，生成新增信息块。
   *
   * <p>当两者都存在时，会用换行符拼接；如果只有一方存在，则直接返回存在的内容。
   * 该方法不做语义压缩，只负责保留原始文本并保证格式稳定。
   */
  private String buildMergedSummary(String previousSummary, String newMessages) {
    StringBuilder builder = new StringBuilder();
    if (StringUtils.hasText(previousSummary)) {
      builder.append(previousSummary.trim());
    }
    if (StringUtils.hasText(newMessages)) {
      if (builder.length() > 0) {
        builder.append("\n");
      }
      builder.append(newMessages.trim());
    }
    return builder.toString();
  }

  /**
   * 从文本中提取指定 key 对应的一行内容。
   *
   * <p>当前实现要求文本遵循“key: value”的格式，
   * 遍历每一行后找到第一个匹配项并返回其值部分。
   * 如果未找到则返回空字符串。
   */
  private String extractLine(String text, String key) {
    if (!StringUtils.hasText(text)) {
      return "";
    }
    for (String line : text.split("\\r?\\n")) {
      if (line.startsWith(key + ":")) {
        return line.substring((key + ":").length()).trim();
      }
    }
    return "";
  }
}
