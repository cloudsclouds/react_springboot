package com.example.server_springboot.ai.editor.dto;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EditorOrchestrationResult {
  private EditorTaskAgent primaryAgent;
  private EditorAiExecuteResponse response;
  private Map<String, Object> orchestrationMeta;
}
