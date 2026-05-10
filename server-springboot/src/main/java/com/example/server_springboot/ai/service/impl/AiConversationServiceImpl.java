package com.example.server_springboot.ai.service.impl;

import com.example.server_springboot.ai.dto.ConversationDetailResponse;
import com.example.server_springboot.ai.dto.ConversationListItemResponse;
import com.example.server_springboot.ai.dto.ConversationMessageResponse;
import com.example.server_springboot.ai.dto.CreateConversationRequest;
import com.example.server_springboot.ai.dto.CreateConversationResponse;
import com.example.server_springboot.ai.entity.AiConversation;
import com.example.server_springboot.ai.entity.AiConversationMessage;
import com.example.server_springboot.ai.mapper.AiConversationMapper;
import com.example.server_springboot.ai.mapper.AiConversationMessageMapper;
import com.example.server_springboot.ai.service.AiConversationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
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
   * 会话表的 MyBatis Mapper，只保留最直接的插入与查询能力
   */
  private final AiConversationMapper aiConversationMapper;
  private final AiConversationMessageMapper aiConversationMessageMapper;

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

    // 新建会话时摘要默认设置为空对话，便于前端列表和详情页展示统一的初始状态。
    conversation.setSummary("空对话");

    // 0 表示正常可用状态，后续如果做软删除可以改为其他状态值。
    conversation.setStatus(0);

    // 创建时间和更新时间都写当前时间，方便后续排序和审计。
    conversation.setCreatedAt(LocalDateTime.now());
    conversation.setUpdatedAt(LocalDateTime.now());

    // 持久化到数据库，插入成功后，conversation.getId() 会被回填。
    aiConversationMapper.insert(conversation);

    // 返回给前端的最小必要信息：会话 ID、标题和所属用户。
    return new CreateConversationResponse(conversation.getId(), title, userId);
  }

  @Override
  public List<ConversationListItemResponse> listConversations(Long userId) {
    return aiConversationMapper.selectByUserId(userId).stream()
        .map(conversation -> {
          Long messageCount = 0L;
          LocalDateTime lastMessageAt = conversation.getUpdatedAt();
          return new ConversationListItemResponse(
              conversation.getId(),
              conversation.getTitle(),
              conversation.getSummary(),
              lastMessageAt,
              messageCount);
        })
        .collect(Collectors.toList());
  }

  @Override
  public ConversationDetailResponse getConversationDetail(Long conversationId, Long userId) {
    AiConversation conversation = aiConversationMapper.selectById(conversationId);
    if (conversation == null || !userId.equals(conversation.getUserId())) {
      throw new IllegalArgumentException("会话不存在或无权访问");
    }
    List<ConversationMessageResponse> messages = aiConversationMessageMapper
        .selectByConversationId(conversationId)
        .stream()
        .map(message -> new ConversationMessageResponse(
            message.getId(),
            message.getRole(),
            message.getContent(),
            message.getStatus(),
            message.getRequestId(),
            message.getCreatedAt(),
            message.getUpdatedAt()))
        .collect(Collectors.toList());
    return new ConversationDetailResponse(
        conversation.getId(),
        conversation.getTitle(),
        conversation.getSummary(),
        conversation.getCreatedAt(),
        conversation.getUpdatedAt(),
        messages);
  }
}
