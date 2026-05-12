package com.example.server_springboot.ai.editor.agent;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import java.util.Map;

/**
 * 编辑器任务智能体
 */
public interface EditorTaskAgent {
  // 动作
  String action();
  
  // 意图
  String intent();
  
  // 输出类型
  String outputType();

  // 回填动作
  String resultAction();

  // 生成
  String generate(EditorAiExecuteRequest request);

  // 元数据
  default Map<String, Object> meta(EditorAiExecuteRequest request) {
    return Map.of("action", request.getAction(), "entryPoint", request.getEntryPoint());
  }

  // 支持
  /**
   * 支持
   * @param action 动作
   * @param request 请求
   * @return 是否支持
   */
  default boolean supports(String action, EditorAiExecuteRequest request) {
    return action() != null && action().equalsIgnoreCase(action);
  }
}
