package com.example.server_springboot.ai.memory.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 统一记忆实体。
 *
 * 同一张表承载 L1 / L2 / L3 三类记忆，其中：
 * - L1 表示最近原文窗口，偏短期、频繁写入；
 * - L2 表示滚动摘要，承载阶段性上下文；
 * - L3 表示长期语义记忆，保存稳定偏好、事实与项目背景。
 *
 * 通过 level、scope、confidence、ttlAt 和 sourceType 等字段，
 * 可以区分记忆层级、适用范围、来源可信度以及生命周期。
 */
@Data
public class MemoryRecord {
  /** 主键。 */
  private Long id;
  /** 所属用户。 */
  private Long userId;
  /** 所属会话，L1/L2 通常会用到。 */
  private Long conversationId;
  /** 层级：L1 / L2 / L3。 */
  private MemoryLevel level;
  /** 作用域：SESSION / USER / PROJECT。 */
  private MemoryScope scope;
  /** 原始内容，通常是原文或抽取出的短文本。 */
  private String content;
  /** 摘要，L2 滚动摘要或 L3 的结构化摘要。 */
  private String summary;
  /** 结构化事实内容，通常用 JSON 保存。 */
  private String factsJson;
  /** 来源类型，例如用户确认、自动抽取等。 */
  private String sourceType;
  /** 来源轮次或请求标识，用于追踪是从哪一轮产生的。 */
  private String sourceTurn;
  /** 置信度，数值越高表示越可信。 */
  private Double confidence;
  /** 命中次数，用于热度和去重合并参考。 */
  private Integer hitCount;
  /** 版本号，用于记录同一的演进。 */
  private Integer version;
  /** 状态，例如 ACTIVE / ARCHIVED / EXPIRED。 */
  private String status;
  /** 过期时间，到期后不再参与读取。 */
  private LocalDateTime ttlAt;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
