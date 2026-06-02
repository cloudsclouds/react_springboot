package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;
import com.example.server_springboot.ai.memory.entity.MemoryLevel;
import com.example.server_springboot.ai.memory.entity.MemorySourceType;
import com.example.server_springboot.ai.memory.service.MemoryGatingPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultMemoryGatingPolicy implements MemoryGatingPolicy {
  private static final double MIN_L3_CONFIDENCE = 0.8D;

  @Override
  public boolean shouldWrite(MemoryWriteCommand command) {
    if (command == null || command.getLevel() == null) {
      return false;
    }
    // L1只要 content 有内容就允许写入，L2只要 summary 有内容就允许写入，L3只要 content 有内容且 confidence 大于 0.8 就允许写入
    if (command.getLevel() == MemoryLevel.L1) {
      return StringUtils.hasText(command.getContent());
    }
    if (command.getLevel() == MemoryLevel.L2) {
      return StringUtils.hasText(command.getSummary());
    }
    if (command.getLevel() == MemoryLevel.L3) {
      // L3 需要满足以下条件：
      // 1. 来源类型是用户确认、多轮重复或手动更新
      // 2. confidence 大于 0.8
      // 3. content 有内容
      boolean strongSource = command.getSourceType() == MemorySourceType.USER_CONFIRMATION
          || command.getSourceType() == MemorySourceType.MULTI_TURN_REPEAT
          || command.getSourceType() == MemorySourceType.MANUAL_UPDATE;
      boolean confidenceOk = command.getConfidence() != null && command.getConfidence() >= MIN_L3_CONFIDENCE;
      return strongSource && confidenceOk && StringUtils.hasText(command.getContent());
    }
    return false;
  }
}
