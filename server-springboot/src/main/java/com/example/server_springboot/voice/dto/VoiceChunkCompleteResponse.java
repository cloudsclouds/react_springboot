package com.example.server_springboot.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoiceChunkCompleteResponse {
  private String audioId;
  private String fileName;
}
