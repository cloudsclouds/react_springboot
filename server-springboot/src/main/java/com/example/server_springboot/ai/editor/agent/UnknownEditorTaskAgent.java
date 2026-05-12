package com.example.server_springboot.ai.editor.agent;

import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UnknownEditorTaskAgent implements EditorTaskAgent {
  @Override
  public String action() {
    return "unknown";
  }

  @Override
  public String intent() {
    return "unknown";
  }

  @Override
  public String outputType() {
    return "markdown";
  }

  @Override
  public String resultAction() {
    return "previewOnly";
  }

  @Override
  public String generate(EditorAiExecuteRequest request) {
    return "我暂时无法判断你的意图。你可以明确说明要我做哪一种：续写（continue）、润色（polish）、总结（summary）、翻译（translate）或 Mermaid（mermaid）。";
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return Map.of("action", request.getAction(), "intent", "unknown");
  }
}
