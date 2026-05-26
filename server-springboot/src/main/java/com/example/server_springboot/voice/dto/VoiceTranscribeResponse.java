package com.example.server_springboot.voice.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoiceTranscribeResponse {
  private String text;
  private List<VoiceSegmentResponse> segments;
}
