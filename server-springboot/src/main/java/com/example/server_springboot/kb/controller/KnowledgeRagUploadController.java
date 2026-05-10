package com.example.server_springboot.kb.controller;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeRagUploadResponse;
import com.example.server_springboot.kb.service.KnowledgeArticleUploadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/kb/rag")
@RequiredArgsConstructor
public class KnowledgeRagUploadController {
  private final KnowledgeArticleUploadService uploadService;

  @PostMapping("/upload")
  public ApiResponse<List<KnowledgeRagUploadResponse>> upload(@RequestParam("files") List<MultipartFile> files) {
    return uploadService.uploadAndIngest(files, UserContext.getUserId());
  }
}
