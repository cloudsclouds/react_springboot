package com.example.server_springboot.ai.memory.controller;

import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;
import com.example.server_springboot.ai.memory.service.MemoryContextBuilder;
import com.example.server_springboot.ai.memory.service.MemoryOrchestratorService;
import com.example.server_springboot.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
public class MemoryController {
  private final MemoryContextBuilder memoryContextBuilder;
  private final MemoryOrchestratorService memoryOrchestratorService;

  @GetMapping("/context")
  public ApiResponse<MemoryContext> context(@RequestParam Long userId,
                                            @RequestParam Long conversationId,
                                            @RequestParam(required = false) String query) {
    return ApiResponse.success("查询成功", memoryContextBuilder.build(userId, conversationId, query));
  }

  @PostMapping("/write")
  public ApiResponse<Void> write(@Valid @RequestBody MemoryWriteCommand command) {
    memoryOrchestratorService.write(command);
    return ApiResponse.success("写入成功", null);
  }
}
