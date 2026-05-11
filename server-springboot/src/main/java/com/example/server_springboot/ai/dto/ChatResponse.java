package com.example.server_springboot.ai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {
  private String answer;
  private List<ChatCitationDto> citations;
  private Boolean ragUsed;
}
