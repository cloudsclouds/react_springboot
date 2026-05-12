package com.example.server_springboot.ai.editor.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeArticleOperationLog {
  private Long id;
  private Long userId;
  private Long articleId;
  private Long conversationId;
  private String requestId;
  private String operationType;
  private String changeMode;
  private String intent;
  private String entryPoint;
  private String inputText;
  private String selectedText;
  private String outputText;
  private String resultAction;
  private String beforeSnapshot;
  private String afterSnapshot;
  private String deltaJson;
  private Long refOperationId;
  private String status;
  private String errorMessage;
  private Integer latencyMs;
  private LocalDateTime createdAt;
}
