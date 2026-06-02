package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import com.example.server_springboot.ai.memory.service.MemoryConflictResolver;
import org.springframework.stereotype.Component;

/**
 * 默认记忆冲突解决器。
 *
 * <p>当系统准备写入一条新记忆时，如果数据库中已经存在相同主题/内容的旧记忆，
 * 需要判断应该保留旧数据、合并数据，还是用新数据替换旧数据。
 *
 * <p>当前实现以置信度作为主要判断依据：
 * <ul>
 *   <li>旧记录为空：说明数据库中还没有相同记忆，直接允许替换（本质上是新增）。</li>
 *   <li>新记忆置信度显著更高：说明新信息更可信，用新记忆替换旧记忆。</li>
 *   <li>两者置信度接近：说明两条记忆都具有参考价值，进入合并流程。</li>
 *   <li>新记忆置信度更低：说明旧记录更稳定，优先保留旧记录。</li>
 * </ul>
 *
 * <p>这里的 0.05D 是一个轻微阈值，用于避免因为极小的分数波动而频繁覆盖数据。
 */
@Component
public class DefaultMemoryConflictResolver implements MemoryConflictResolver {

  /**
   * 根据旧记忆和候选记忆的置信度，决定最终的冲突处理策略。
   *
   * <p>返回值含义：
   * <ul>
   *   <li>{@code REPLACE}：用候选记忆替换旧记忆。</li>
   *   <li>{@code MERGE}：旧记忆和候选记忆进行合并。</li>
   *   <li>{@code KEEP_EXISTING}：保留旧记忆，不写入新内容。</li>
   * </ul>
   *
   * @param existing 数据库中已存在的记忆记录，可能为空
   * @param candidate 当前准备写入的记忆记录
   * @return 冲突处理结果
   */
  @Override
  public ResolutionResult resolve(MemoryRecord existing, MemoryRecord candidate) {
    if (existing == null) {
      return ResolutionResult.REPLACE;
    }

    double existingConfidence = existing.getConfidence() == null ? 0D : existing.getConfidence();
    double candidateConfidence = candidate.getConfidence() == null ? 0D : candidate.getConfidence();

    // 候选记忆明显更可信，优先用新数据覆盖旧数据。
    if (candidateConfidence > existingConfidence + 0.05D) {
      return ResolutionResult.REPLACE;
    }

    // 两条记忆可信度接近，认为都具有保留价值，后续由存储层执行字段合并。
    if (Math.abs(candidateConfidence - existingConfidence) <= 0.05D) {
      return ResolutionResult.MERGE;
    }

    // 候选记忆更弱，避免低质量数据污染已有稳定记忆。
    return ResolutionResult.KEEP_EXISTING;
  }
}
