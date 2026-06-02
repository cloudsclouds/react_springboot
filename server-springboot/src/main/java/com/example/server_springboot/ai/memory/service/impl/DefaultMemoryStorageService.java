package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;
import com.example.server_springboot.ai.memory.entity.MemoryLevel;
import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import com.example.server_springboot.ai.memory.mapper.MemoryRecordMapper;
import com.example.server_springboot.ai.memory.service.MemoryConflictResolver;
import com.example.server_springboot.ai.memory.service.MemoryGatingPolicy;
import com.example.server_springboot.ai.memory.service.MemoryStorageService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DefaultMemoryStorageService implements MemoryStorageService {
  private final MemoryRecordMapper memoryRecordMapper;
  private final MemoryGatingPolicy gatingPolicy;
  private final MemoryConflictResolver conflictResolver;

  /**
   * 写入记忆记录。
   *
   * 流程说明：
   * 1. 先通过门控策略判断当前命令是否允许写入；
   * 2. 将写入命令转换为持久化实体；
   * 3. 对 L3 记忆执行去重与冲突处理；
   * 4. 最终执行插入或更新。
   */
  @Override
  public MemoryRecord write(MemoryWriteCommand command) {
    if (!gatingPolicy.shouldWrite(command)) {
      return null;
    }
    MemoryRecord candidate = toRecord(command);
    if (candidate.getLevel() == MemoryLevel.L3) {
      MemoryRecord existing = memoryRecordMapper.selectByUserIdAndContent(command.getUserId(), command.getContent());
      if (existing != null) {
        switch (conflictResolver.resolve(existing, candidate)) {
          case KEEP_EXISTING:
            existing.setHitCount((existing.getHitCount() == null ? 0 : existing.getHitCount()) + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            memoryRecordMapper.update(existing);
            return existing;
          case MERGE:
            existing.setSummary(merge(existing.getSummary(), candidate.getSummary()));
            existing.setFactsJson(merge(existing.getFactsJson(), candidate.getFactsJson()));
            existing.setConfidence(Math.max(existing.getConfidence() == null ? 0D : existing.getConfidence(), candidate.getConfidence() == null ? 0D : candidate.getConfidence()));
            existing.setHitCount((existing.getHitCount() == null ? 0 : existing.getHitCount()) + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            memoryRecordMapper.update(existing);
            return existing;
          case REPLACE:
            candidate.setId(existing.getId());
            candidate.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
            candidate.setHitCount((existing.getHitCount() == null ? 0 : existing.getHitCount()) + 1);
            memoryRecordMapper.update(candidate);
            return candidate;
          case IGNORE:
          default:
            return existing;
        }
      }
    }
    memoryRecordMapper.insert(candidate);
    return candidate;
  }

  /**
   * 查询某个会话下最近写入的记忆记录。
   */
  @Override
  public List<MemoryRecord> recentByConversation(Long conversationId, int limit) {
    if (conversationId == null || limit <= 0) {
      return Collections.emptyList();
    }
    return memoryRecordMapper.selectRecentByConversationId(conversationId, limit);
  }

  /**
   * 按用户和关键词检索 L3 记忆。
   */
  @Override
  public List<MemoryRecord> searchL3(Long userId, String query, int limit) {
    if (userId == null || !StringUtils.hasText(query) || limit <= 0) {
      return Collections.emptyList();
    }
    return memoryRecordMapper.searchByUserId(userId, query.trim(), limit);
  }

  /**
   * 清理过期记忆的扩展入口。
   *
   * <p>当前过期逻辑由 SQL 中的 `ttl_at` 条件控制，这里仅保留调度入口，方便后续扩展物理删除或归档策略。
   */
  @Override
  public void cleanupExpired() {
    // 这里由 SQL 的 ttl_at 条件控制过期淘汰，保留定时钩子以便后续扩展物理清理。
  }

  /**
   * 定时触发过期清理。
   *
   * <p>目前只是调用扩展入口，避免业务写入逻辑与调度逻辑耦合。
   */
  @Scheduled(fixedDelay = 60_000L)
  public void scheduledCleanup() {
    cleanupExpired();
  }

  /**
   * 将写入命令转换为可持久化的记忆实体。
   *
   * <p>这里负责补齐默认值、时间戳、版本号、命中次数和 TTL 等基础字段。
   */
  private MemoryRecord toRecord(MemoryWriteCommand command) {
    LocalDateTime now = LocalDateTime.now();
    MemoryRecord record = new MemoryRecord();
    record.setUserId(command.getUserId());
    record.setConversationId(command.getConversationId());
    record.setLevel(command.getLevel());
    record.setScope(command.getScope());
    record.setContent(command.getContent());
    record.setSummary(command.getSummary());
    record.setFactsJson(command.getFactsJson());
    record.setSourceType(command.getSourceType() == null ? null : command.getSourceType().name());
    record.setSourceTurn(command.getSourceTurn());
    record.setConfidence(command.getConfidence());
    record.setHitCount(1);
    record.setVersion(1);
    record.setStatus("ACTIVE");
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    if (command.getTtlDays() != null && command.getTtlDays() > 0) {
      record.setTtlAt(now.plusDays(command.getTtlDays()));
    } else if (command.getLevel() == MemoryLevel.L3) {
      record.setTtlAt(now.plusDays(30));
    }
    return record;
  }

  /**
   * 合并两个文本字段。
   *
   * <p>若新值为空则保留旧值；若旧值已包含新值则不重复拼接；否则按换行合并。
   */
  private String merge(String oldValue, String newValue) {
    if (!StringUtils.hasText(oldValue)) {
      return newValue;
    }
    if (!StringUtils.hasText(newValue)) {
      return oldValue;
    }
    if (oldValue.contains(newValue)) {
      return oldValue;
    }
    return oldValue + "\n" + newValue;
  }
}
