package com.example.server_springboot.ai.config;

import com.alibaba.dashscope.aigc.generation.Generation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// AI 配置
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  // Generation Bean，用于生成 AI 内容
  @Bean
  public Generation generation() {
    return new Generation();
  }
}
