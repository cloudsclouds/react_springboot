package com.example.server_springboot.ai.editor.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EditorRouteDecision {
  private String primaryIntent;
  private List<String> secondaryIntents;
  private double confidence;
  private String source;
  private boolean explicitAction;

  public Map<String, Object> toMeta() {
    return Map.of(
        "primaryIntent", primaryIntent == null ? "" : primaryIntent,
        "secondaryIntents", secondaryIntents == null ? List.of() : secondaryIntents,
        "confidence", confidence,
        "source", source == null ? "" : source,
        "explicitAction", explicitAction
    );
  }
}
