package com.example.server_springboot.kb.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {
  private static final int VECTOR_DIMENSION = 1024;

  private final RestClient.Builder restClientBuilder;

  @Value("${ai.api-key:}")
  private String apiKey;

  @Value("${ai.embedding-model:qwen-text-embedding-v3}")
  private String embeddingModel;

  @Value("${ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
  private String baseUrl;

  public byte[] embed(String text) {
    if (text == null) {
      text = "";
    }
    if (apiKey == null || apiKey.isBlank()) {
      return fallbackEmbed(text);
    }
    try {
      EmbeddingResponse response = restClientBuilder.build()
          .post()
          .uri(baseUrl + "/embeddings")
          .header("Authorization", "Bearer " + apiKey)
          .body(new EmbeddingRequest(embeddingModel, List.of(text)))
          .retrieve()
          .body(EmbeddingResponse.class);
      if (response != null && response.output != null && response.output.embeddings != null && !response.output.embeddings.isEmpty()) {
        return toBytes(response.output.embeddings.get(0).embedding);
      }
    } catch (Exception ignored) {
      // fall back to local deterministic vector if remote embedding fails
    }
    return fallbackEmbed(text);
  }

  private byte[] fallbackEmbed(String text) {
    double[] vector = new double[VECTOR_DIMENSION];
    String normalized = text.toLowerCase();
    for (String token : normalized.split("\\s+")) {
      if (token.isBlank()) {
        continue;
      }
      int bucket = Math.floorMod(token.hashCode(), VECTOR_DIMENSION);
      vector[bucket] += 1.0d;
    }
    for (int i = 0; i < normalized.length(); i++) {
      int bucket = i % VECTOR_DIMENSION;
      vector[bucket] += (normalized.charAt(i) % 7) * 0.05d;
    }
    return toBytes(vector);
  }

  private byte[] toBytes(double[] vector) {
    ByteBuffer buffer = ByteBuffer.allocate(vector.length * Double.BYTES);
    for (double v : vector) {
      buffer.putDouble(v);
    }
    return buffer.array();
  }

  private byte[] toBytes(List<Double> embedding) {
    double[] vector = new double[embedding.size()];
    for (int i = 0; i < embedding.size(); i++) {
      vector[i] = embedding.get(i) == null ? 0d : embedding.get(i);
    }
    return toBytes(vector);
  }

  private record EmbeddingRequest(String model, List<String> input) {}
  private record EmbeddingOutput(List<EmbeddingVector> embeddings) {}
  private record EmbeddingVector(List<Double> embedding) {}
  private record EmbeddingResponse(EmbeddingOutput output) {}
}
