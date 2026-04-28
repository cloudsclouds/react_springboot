package com.example.server_springboot.interceptor;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // OPTIONS request should pass through for CORS
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setCharacterEncoding("UTF-8");
      response.setContentType("application/json");
      response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized: No Token\"}");
      return false;
    }

    String token = header.substring(7);
    Long userId = JwtUtils.verifyToken(token);

    if (userId == null) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setCharacterEncoding("UTF-8");
      response.setContentType("application/json");
      response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized: Invalid Token\"}");
      return false;
    }

    UserContext.setUserId(userId);
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    UserContext.clear();
  }
}
