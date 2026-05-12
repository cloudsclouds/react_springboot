package com.example.server_springboot.ai.editor.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EditorAiExecuteResponse {
  private String intent; // 意图类型
  private String outputType; // 输出类型
  private String outputText; // 输出文本
  private String resultAction; // 回填动作
  private Map<String, Object> meta; // 元数据
}
