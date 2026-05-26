package com.example.server_springboot.voice.dto;

import lombok.Data;

@Data
public class VoicePlaybackClearRequest {
  private String audioId;
  private String source;
}
