package com.example.server_springboot.voice.service.impl;

import com.example.server_springboot.voice.dto.VoiceChunkCompleteResponse;
import com.example.server_springboot.voice.service.VoiceStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceStorageServiceImpl implements VoiceStorageService {

  private final Path baseDir = Path.of("server-springboot", "storage", "voice");
  private final Path chunkDir = baseDir.resolve("chunks");
  private final Path audioDir = baseDir.resolve("audio");

  @Override
  public void uploadChunk(String sessionId, Integer chunkIndex, Boolean isLastChunk, MultipartFile file) throws IOException {
    if (!StringUtils.hasText(sessionId)) {
      throw new IllegalArgumentException("sessionId 不能为空");
    }
    if (chunkIndex == null || chunkIndex < 0) {
      throw new IllegalArgumentException("chunkIndex 非法");
    }
    if (file == null || file.isEmpty()) {
      return;
    }

    Files.createDirectories(chunkDir.resolve(sessionId));
    Path target = chunkDir.resolve(sessionId).resolve(chunkIndex + ".webm");
    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public VoiceChunkCompleteResponse complete(String sessionId) throws IOException {
    if (!StringUtils.hasText(sessionId)) {
      throw new IllegalArgumentException("sessionId 不能为空");
    }

    Path sessionPath = chunkDir.resolve(sessionId);
    if (!Files.exists(sessionPath)) {
      throw new IllegalArgumentException("未找到分片会话");
    }

    Files.createDirectories(audioDir);
    String audioId = "audio-" + sessionId;
    String fileName = audioId + ".webm";
    Path target = audioDir.resolve(fileName);

    try (Stream<Path> stream = Files.list(sessionPath)) {
      List<Path> chunks = stream
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparingInt(path -> Integer.parseInt(path.getFileName().toString().replace(".webm", ""))))
          .collect(Collectors.toList());

      Files.deleteIfExists(target);
      for (Path chunk : chunks) {
        Files.write(target, Files.readAllBytes(chunk), Files.exists(target) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
      }
    }

    return new VoiceChunkCompleteResponse(audioId, fileName);
  }

  @Override
  public byte[] readAudio(String audioId) throws IOException {
    Path file = audioDir.resolve(audioId + ".webm");
    if (!Files.exists(file)) {
      throw new IllegalArgumentException("音频不存在");
    }
    return Files.readAllBytes(file);
  }

  @Override
  public String detectContentType(String audioId) {
    return "audio/webm";
  }
}
