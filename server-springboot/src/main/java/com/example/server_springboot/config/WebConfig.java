package com.example.server_springboot.config;

import com.example.server_springboot.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  /**
   * JWT 鉴权拦截器，由 Spring 自动注入。
   * 用于在请求进入 Controller 前校验 token 的合法性。
   */
  private final JwtInterceptor jwtInterceptor;
  private final String corsAllowedOrigins;

  /**
   * 构造器注入 JwtInterceptor 与 CORS 来源配置。
   * @param jwtInterceptor JWT 鉴权拦截器实例
   * @param corsAllowedOrigins 允许跨域的来源（逗号分隔）
   */
  public WebConfig(
      JwtInterceptor jwtInterceptor,
      @Value("${app.cors.allowed-origins:http://localhost:3000}") String corsAllowedOrigins) {
    this.jwtInterceptor = jwtInterceptor;
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  /**
   * 注册 Spring MVC 拦截器。
   *   对 /api/** 下的接口统一进行 JWT 校验；
   *   放行 /api/auth/**（登录/注册等无需 token 的接口）；
   *   放行 api/public/**（公开访问接口）。
   * @param registry 拦截器注册器
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/auth/**",
            "/api/public/**",
            "/api/voice/recordings/open-source-demo/blob");
  }

  /**
   * 配置跨域（CORS）规则。
   * 
   *   仅对 /api/** 生效；
   *   允许前端来源：http://localhost:3000；
   *   允许常见 HTTP 方法（含 OPTIONS 预检请求）；
   *   允许所有请求头。
   * @param registry CORS 注册器
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
      .addMapping("/api/**")
      .allowedOrigins(corsAllowedOrigins.split(","))
      .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
      .allowedHeaders("*")
      .allowCredentials(true);
  }
}