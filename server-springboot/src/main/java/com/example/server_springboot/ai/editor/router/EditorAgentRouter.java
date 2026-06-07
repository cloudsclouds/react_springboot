package com.example.server_springboot.ai.editor.router;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorRouteDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 编辑器任务路由器
 */
@Component
public class EditorAgentRouter {
  // 默认动作 
  private static final String DEFAULT_ACTION = "polish";
  // 意图检测动作
  private static final String INTENT_DETECT_ACTION = "intent-detect";

  // 编辑器任务智能体列表
  private final List<EditorTaskAgent> agents;

  /**
   * 构造器注入编辑器任务智能体列表
   * @param agents 编辑器任务智能体列表
   */
  public EditorAgentRouter(List<EditorTaskAgent> agents) {
    this.agents = agents;
  }

  /**
   * 路由编辑器任务
   * @param request 请求
   * @return 编辑器任务
   */
  public EditorTaskAgent route(EditorAiExecuteRequest request) {
    return route(routeDecision(request));
  }

  public EditorTaskAgent route(EditorRouteDecision decision) {
    String action = decision == null ? DEFAULT_ACTION : normalizeAction(decision.getPrimaryIntent());
    // 根据动作查找编辑器任务智能体，如果找不到，则返回默认动作
    return agents.stream()
        .filter(agent -> agent.action().equalsIgnoreCase(action))  
        .findFirst()
        .orElseGet(() -> agents.stream()
            .filter(agent -> agent.action().equalsIgnoreCase(DEFAULT_ACTION))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的编辑器 AI action: " + action)));
  }

  public EditorRouteDecision routeDecision(EditorAiExecuteRequest request) {
    if (request == null) {
      return new EditorRouteDecision(DEFAULT_ACTION, List.of(), 0.5d, "default", false);
    }

    if (StringUtils.hasText(request.getAction())) {
      String normalized = normalizeAction(request.getAction());
      return new EditorRouteDecision(normalized, suggestSecondaryIntents(request, normalized), 0.99d, "explicit", true);
    }

    EditorTaskAgent intentAgent = agents.stream()
        .filter(agent -> INTENT_DETECT_ACTION.equalsIgnoreCase(agent.action()))
        .findFirst()
        .orElse(null);
    if (intentAgent == null) {
      return new EditorRouteDecision(DEFAULT_ACTION, suggestSecondaryIntents(request, DEFAULT_ACTION), 0.55d, "fallback", false);
    }

    String detected = normalizeAction(intentAgent.generate(request));
    double confidence = "unknown".equals(detected) ? 0.38d : 0.84d;
    return new EditorRouteDecision(detected, suggestSecondaryIntents(request, detected), confidence, "intent-detect", false);
  }

  /**
   * 解析动作
   * @param request 请求
   * @return 动作 默认动作
   */
  private String resolveAction(EditorAiExecuteRequest request) {
    if (request == null) {
      // 如果请求为空，则返回默认动作
      return DEFAULT_ACTION;
    }
    // 如果请求有动作，则返回请求的动作
    if (StringUtils.hasText(request.getAction())) {
      return request.getAction();
    }
    // 如果请求没有动作，则查找意图检测动作
    EditorTaskAgent intentAgent = agents.stream()
        .filter(agent -> INTENT_DETECT_ACTION.equalsIgnoreCase(agent.action()))
        .findFirst()
        .orElse(null);
    // 如果意图检测动作为空，则返回默认动作
    if (intentAgent == null) {
      return DEFAULT_ACTION;
    }
    // 归一化动作
    return normalizeAction(intentAgent.generate(request));
  }

  private List<String> suggestSecondaryIntents(EditorAiExecuteRequest request, String primary) {
    String text = ((request == null ? "" : request.getChatInput()) + " "
        + (request == null ? "" : request.getSelectedText()) + " "
        + (request == null ? "" : request.getSurroundingContext())).toLowerCase(Locale.ROOT);
    List<String> suggestions = new ArrayList<>();

    if (text.contains("润色") || text.contains("改写") || text.contains("优化")) {
      suggestions.add("polish");
    }
    if (text.contains("总结") || text.contains("概括") || text.contains("提炼")) {
      suggestions.add("summary");
    }
    if (text.contains("翻译") || text.contains("英文") || text.contains("中文")) {
      suggestions.add("translate");
    }
    if (text.contains("续写") || text.contains("补充") || text.contains("扩写")) {
      suggestions.add("continue");
    }
    suggestions.removeIf(item -> !StringUtils.hasText(item) || item.equalsIgnoreCase(primary));
    if (suggestions.isEmpty() && !"polish".equals(primary)) {
      suggestions.add("polish");
    }
    return suggestions.stream().distinct().limit(2).toList();
  }

  /**
   * 归一化动作
   * @param action 动作
   * @return 归一化动作 默认动作
   */
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
