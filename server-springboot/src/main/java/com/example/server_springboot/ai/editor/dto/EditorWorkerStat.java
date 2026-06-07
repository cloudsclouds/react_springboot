package com.example.server_springboot.ai.editor.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EditorWorkerStat {
  private String worker;
  private String status;
  private long latencyMs;
  private int retryCount;
  private int outputLength;

  public Map<String, Object> toMeta() {
    return Map.of(
        "worker", worker == null ? "" : worker,
        "status", status == null ? "" : status,
        "latencyMs", latencyMs,
        "retryCount", retryCount,
        "outputLength", outputLength
    );
  }
}
