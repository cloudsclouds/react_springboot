package com.example.server_springboot.voice.dto;

import lombok.Data;

@Data
public class VoiceChunkCompleteRequest {
  private String sessionId;
}
