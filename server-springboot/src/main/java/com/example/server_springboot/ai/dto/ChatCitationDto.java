package com.example.server_springboot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatCitationDto {
  private String citationId;
  private Long articleId;
  private String articleTitle;
  private Long chunkId;
  private Integer chunkIndex;
  private String chunkText;
  private Double score;
}
