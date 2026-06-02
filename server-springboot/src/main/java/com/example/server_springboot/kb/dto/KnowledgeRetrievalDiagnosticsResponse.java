package com.example.server_springboot.kb.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeRetrievalDiagnosticsResponse {
  private String query;
  private String compressedQuery;
  private List<String> subQueries;
  private boolean lowConfidence;
  private String refusalReason;
  private List<KnowledgeRetrievalEvalResponse> evaluations;
}
