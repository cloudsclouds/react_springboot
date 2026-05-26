package com.example.server_springboot.voice.controller;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.voice.dto.VoiceChunkCompleteRequest;
import com.example.server_springboot.voice.dto.VoiceChunkCompleteResponse;
import com.example.server_springboot.voice.dto.VoiceChunkUploadResponse;
import com.example.server_springboot.voice.dto.VoicePlaybackClearRequest;
import com.example.server_springboot.voice.dto.VoiceSegmentResponse;
import com.example.server_springboot.voice.dto.VoiceTranscribeRequest;
import com.example.server_springboot.voice.dto.VoiceTranscribeResponse;
import com.example.server_springboot.voice.service.VoiceStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

  private final VoiceStorageService voiceStorageService;

  @PostMapping("/recordings/chunks/upload")
  public ApiResponse<VoiceChunkUploadResponse> uploadChunk(
      @RequestParam("file") MultipartFile file,
      @RequestParam("sessionId") String sessionId,
      @RequestParam("chunkIndex") Integer chunkIndex,
      @RequestParam("isLastChunk") Boolean isLastChunk) throws IOException {
    voiceStorageService.uploadChunk(sessionId, chunkIndex, isLastChunk, file);
    return ApiResponse.success("分片接收成功", new VoiceChunkUploadResponse(true));
  }

  @PostMapping("/recordings/chunks/complete")
  public ApiResponse<VoiceChunkCompleteResponse> complete(@RequestBody VoiceChunkCompleteRequest request) throws IOException {
    return ApiResponse.success("合并成功", voiceStorageService.complete(request.getSessionId()));
  }

  @GetMapping("/recordings/{audioId}/blob")
  public ResponseEntity<byte[]> getBlob(@PathVariable String audioId) throws IOException {
    byte[] bytes = voiceStorageService.readAudio(audioId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + audioId + ".webm\"")
        .contentType(MediaType.parseMediaType(voiceStorageService.detectContentType(audioId)))
        .body(bytes);
  }

  @PostMapping("/transcribe")
  public ApiResponse<VoiceTranscribeResponse> transcribe(@RequestBody VoiceTranscribeRequest request) {
    // MVP: 先返回模拟转写
    VoiceTranscribeResponse response = new VoiceTranscribeResponse(
        "这是模拟转写结果：你好，这是一段来自工作台语音输入模块的示例文本。",
        List.of(
            new VoiceSegmentResponse(0, 3, "你好，这是一段示例录音。"),
            new VoiceSegmentResponse(3, 7, "当前展示的是分段转写结果。"),
            new VoiceSegmentResponse(7, 11, "后续可用于录音质检时间点定位。")
        )
    );
    return ApiResponse.success("转写成功", response);
  }

  @PostMapping("/playback/clear")
  public ApiResponse<Boolean> clearPlayback(@RequestBody VoicePlaybackClearRequest request) {
    return ApiResponse.success("播放进度已清空", true);
  }

  @GetMapping("/recordings/open-source-demo/blob")
  public ResponseEntity<byte[]> openSourceDemoBlob() throws IOException {
    Path samplePath = Paths.get("../docs/voice-sample.wav").normalize();
    if (!Files.exists(samplePath) || !Files.isRegularFile(samplePath)) {
      throw new IOException("voice-sample.wav 不存在，请确认文件路径: docs/voice-sample.wav");
    }

    byte[] bytes = Files.readAllBytes(samplePath);
    if (bytes.length == 0) {
      throw new IOException("voice-sample.wav 文件为空");
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"voice-sample.wav\"")
        .contentType(MediaType.parseMediaType("audio/wav"))
        .contentLength(bytes.length)
        .body(bytes);
  }
}
