package com.example.server_springboot.voice.service;

import com.example.server_springboot.voice.dto.VoiceChunkCompleteResponse;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface VoiceStorageService {
  void uploadChunk(String sessionId, Integer chunkIndex, Boolean isLastChunk, MultipartFile file) throws IOException;

  VoiceChunkCompleteResponse complete(String sessionId) throws IOException;

  byte[] readAudio(String audioId) throws IOException;

  String detectContentType(String audioId);
}
