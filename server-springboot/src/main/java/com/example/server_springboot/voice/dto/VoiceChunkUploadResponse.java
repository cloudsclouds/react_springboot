package com.example.server_springboot.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoiceChunkUploadResponse {
  private boolean accepted;
}
