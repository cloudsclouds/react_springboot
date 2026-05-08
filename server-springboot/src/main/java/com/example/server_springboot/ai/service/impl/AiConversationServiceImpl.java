package com.example.server_springboot.ai.service.impl;

import com.example.server_springboot.ai.dto.CreateConversationRequest;
import com.example.server_springboot.ai.dto.CreateConversationResponse;
import com.example.server_springboot.ai.entity.AiConversation;
import com.example.server_springboot.ai.mapper.AiConversationMapper;
import com.example.server_springboot.ai.service.AiConversationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 会话服务实现类
 */
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

  /**
   * 会话表的 MyBatis Mapper，只保留最直接的插入能力
   */
  private final AiConversationMapper aiConversationMapper;

  @Override
  public CreateConversationResponse createConversation(CreateConversationRequest request, Long userId) {
    // 前端如果没有传标题，后端默认使用“新对话”，保证 UI 里一定有可展示的名称。
    String title = StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : "新对话";

    // 构造会话实体，准备写入数据库。
    AiConversation conversation = new AiConversation();

    // 会话必须绑定当前登录用户，避免出现“无主会话”。
    conversation.setUserId(userId);

    // 写入标题，作为侧边栏会话列表显示内容。
    conversation.setTitle(title);

    // 0 表示正常可用状态，后续如果做软删除可以改成其他状态值。
    conversation.setStatus(0);

    // 创建时间和更新时间都写当前时间，方便后续排序和审计。
    conversation.setCreatedAt(LocalDateTime.now());
    conversation.setUpdatedAt(LocalDateTime.now());

    // 持久化到数据库，插入成功后，conversation.getId() 会被回填。
    aiConversationMapper.insert(conversation);

    // 返回给前端的最小必要信息：会话 ID、标题和所属用户。
    return new CreateConversationResponse(conversation.getId(), title, userId);
  }
}
