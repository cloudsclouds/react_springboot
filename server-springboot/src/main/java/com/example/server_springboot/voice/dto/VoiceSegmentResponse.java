package com.example.server_springboot.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoiceSegmentResponse {
  private Integer startSec;
  private Integer endSec;
  private String text;
}
