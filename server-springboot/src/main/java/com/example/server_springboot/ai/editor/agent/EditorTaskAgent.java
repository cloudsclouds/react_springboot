package com.example.server_springboot.ai.editor.agent;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import java.util.Map;

public interface EditorTaskAgent {
  String action();

  String intent();

  String outputType();

  String resultAction();

  String generate(EditorAiExecuteRequest request);

  default Map<String, Object> meta(EditorAiExecuteRequest request) {
    return Map.of("action", request.getAction(), "entryPoint", request.getEntryPoint());
  }

  default boolean supports(String action, EditorAiExecuteRequest request) {
    return action() != null && action().equalsIgnoreCase(action);
  }
}
