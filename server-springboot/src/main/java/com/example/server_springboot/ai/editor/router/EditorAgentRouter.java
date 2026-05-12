package com.example.server_springboot.ai.editor.router;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EditorAgentRouter {
  private static final String DEFAULT_ACTION = "polish";
  private static final String INTENT_DETECT_ACTION = "intent-detect";

  private final List<EditorTaskAgent> agents;

  public EditorAgentRouter(List<EditorTaskAgent> agents) {
    this.agents = agents;
  }

  public EditorTaskAgent route(EditorAiExecuteRequest request) {
    String action = resolveAction(request);
    return agents.stream()
        .filter(agent -> agent.action().equalsIgnoreCase(action))
        .findFirst()
        .orElseGet(() -> agents.stream()
            .filter(agent -> agent.action().equalsIgnoreCase(DEFAULT_ACTION))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的编辑器 AI action: " + action)));
  }

  private String resolveAction(EditorAiExecuteRequest request) {
    if (request == null) {
      return DEFAULT_ACTION;
    }
    if (StringUtils.hasText(request.getAction())) {
      return request.getAction();
    }
    EditorTaskAgent intentAgent = agents.stream()
        .filter(agent -> INTENT_DETECT_ACTION.equalsIgnoreCase(agent.action()))
        .findFirst()
        .orElse(null);
    if (intentAgent == null) {
      return DEFAULT_ACTION;
    }
    return normalizeAction(intentAgent.generate(request));
  }

  private String normalizeAction(String action) {
    if (!StringUtils.hasText(action)) {
      return DEFAULT_ACTION;
    }
    String normalized = action.trim().toLowerCase();
    return switch (normalized) {
      case "continue", "polish", "summary", "translate", "mermaid", "unknown" -> normalized;
      default -> "unknown";
    };
  }
}
