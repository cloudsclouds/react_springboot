package com.example.server_springboot.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

  /**
   * 大模型 API Key
   */
  private String apiKey;

  /**
   * 大模型服务地址
   */
  private String baseUrl;

  /**
   * 模型名称
   */
  private String modelName;

  /**
   * 生成随机性
   */
  private Double temperature = 0.7;

  /**
   * 最大输出 token 数
   */
  private Integer maxTokens = 2048;
}
