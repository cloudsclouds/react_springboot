package com.example.server_springboot.ai.editor.service.impl;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorMemoryContext;
import com.example.server_springboot.ai.editor.service.EditorMemoryService;
import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.service.MemoryContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultEditorMemoryService implements EditorMemoryService {
  private final MemoryContextBuilder memoryContextBuilder;

  @Override
  public EditorMemoryContext buildContext(Long userId, EditorAiExecuteRequest request) {
    MemoryContext context = memoryContextBuilder.build(userId, request.getArticleId(), buildQuery(request));
    return EditorMemoryContext.builder()
        .recentWindow(context == null ? null : context.getRecentWindow())
        .rollingSummary(context == null ? null : context.getRollingSummary())
        .longTermMemories(context == null ? null : context.getLongTermMemories())
        .build();
  }

  private String buildQuery(EditorAiExecuteRequest request) {
    StringBuilder builder = new StringBuilder();
    if (request.getChatInput() != null) {
      builder.append(request.getChatInput()).append(' ');
    }
    if (request.getSelectedText() != null) {
      builder.append(request.getSelectedText()).append(' ');
    }
    if (request.getSurroundingContext() != null) {
      builder.append(request.getSurroundingContext());
    }
    return builder.toString().trim();
  }
}
