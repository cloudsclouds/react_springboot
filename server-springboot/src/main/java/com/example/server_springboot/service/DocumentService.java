package com.example.server_springboot.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import java.util.List;
import java.util.Map;

public interface DocumentService {
  ApiResponse<Map<String, Long>> createDocument(CreateDocumentRequest request);
  ApiResponse<List<DocumentResponse>> getDocumentList();
  ApiResponse<DocumentResponse> getDocumentMetadata(Long id);
  ApiResponse<String> updateDocumentTitle(Long id, UpdateDocumentRequest request);
  ApiResponse<String> updateDocumentSnapshot(Long id, UpdateDocumentRequest request);
  ApiResponse<String> deleteDocument(Long id);
}
