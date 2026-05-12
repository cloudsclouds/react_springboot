package com.example.server_springboot.ai.editor.router;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import java.util.List;
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
    // 解析动作
    String action = resolveAction(request);
    // 根据动作查找编辑器任务智能体，如果找不到，则返回默认动作
    return agents.stream()
        .filter(agent -> agent.action().equalsIgnoreCase(action))  
        .findFirst()
        .orElseGet(() -> agents.stream()
            .filter(agent -> agent.action().equalsIgnoreCase(DEFAULT_ACTION))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的编辑器 AI action: " + action)));
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
