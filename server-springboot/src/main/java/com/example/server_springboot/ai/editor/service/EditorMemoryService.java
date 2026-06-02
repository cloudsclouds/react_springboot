package com.example.server_springboot.ai.editor.service;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorMemoryContext;

public interface EditorMemoryService {
  EditorMemoryContext buildContext(Long userId, EditorAiExecuteRequest request);
}
