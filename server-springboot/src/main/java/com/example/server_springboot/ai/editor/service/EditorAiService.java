package com.example.server_springboot.ai.editor.service;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteResponse;
import com.example.server_springboot.ai.editor.dto.EditorAiStreamRequest;
import com.example.server_springboot.ai.editor.entity.KnowledgeArticleOperationLog;
import com.example.server_springboot.dto.ApiResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface EditorAiService {
  ApiResponse<EditorAiExecuteResponse> execute(EditorAiExecuteRequest request, Long userId);

  SseEmitter stream(EditorAiStreamRequest request, Long userId);

  ApiResponse<String> stop(Long articleId, String requestId, Long userId);

  ApiResponse<List<KnowledgeArticleOperationLog>> listLogs(Long articleId, Long userId);
}
