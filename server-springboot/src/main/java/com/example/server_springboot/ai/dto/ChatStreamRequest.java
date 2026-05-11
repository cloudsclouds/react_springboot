package com.example.server_springboot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatStreamRequest {

  @NotNull(message = "conversationId 不能为空")
  private Long conversationId;

  @NotBlank(message = "message 不能为空")
  private String message;

  @NotBlank(message = "requestId 不能为空")
  private String requestId;

  /** 是否启用 RAG 检索，默认开启。 */
  private Boolean useRag = true;

  /** RAG 检索限定文章 ID，可选。 */
  private Long articleId;

  /** 返回引用条数，默认 5。 */
  private Integer topK = 5;
}
