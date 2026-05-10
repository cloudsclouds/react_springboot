package com.example.server_springboot.kb.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeRagUploadResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeArticleUploadService {
  ApiResponse<List<KnowledgeRagUploadResponse>> uploadAndIngest(List<MultipartFile> files, Long userId);
}
