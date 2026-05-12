package com.example.server_springboot.ai.editor.controller;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteResponse;
import com.example.server_springboot.ai.editor.dto.EditorAiStreamRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiStopRequest;
import com.example.server_springboot.ai.editor.entity.KnowledgeArticleOperationLog;
import com.example.server_springboot.ai.editor.service.EditorAiService;
import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EditorAiController {
  private final EditorAiService editorAiService;

  @PostMapping("/ai/editor/execute")
  public ApiResponse<EditorAiExecuteResponse> execute(@Valid @RequestBody EditorAiExecuteRequest request) {
    return editorAiService.execute(request, UserContext.getUserId());
  }

  @PostMapping(value = "/ai/editor/stream", produces = "text/event-stream")
  public SseEmitter stream(@Valid @RequestBody EditorAiStreamRequest request) {
    return editorAiService.stream(request, UserContext.getUserId());
  }

  @PostMapping("/ai/editor/stop")
  public ApiResponse<String> stop(@Valid @RequestBody EditorAiStopRequest request) {
    return editorAiService.stop(request.getArticleId(), request.getRequestId(), UserContext.getUserId());
  }

  @GetMapping("/kb/articles/{articleId}/ai-logs")
  public ApiResponse<List<KnowledgeArticleOperationLog>> listLogs(@PathVariable Long articleId) {
    return editorAiService.listLogs(articleId, UserContext.getUserId());
  }
}
