package com.example.server_springboot.controller;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping
  public ApiResponse<Map<String, Long>> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
    return documentService.createDocument(request);
  }

  @GetMapping
  public ApiResponse<List<DocumentResponse>> getDocumentList() {
    return documentService.getDocumentList();
  }

  @GetMapping("/{id}")
  public ApiResponse<DocumentResponse> getDocumentMetadata(@PathVariable Long id) {
    return documentService.getDocumentMetadata(id);
  }

  @PutMapping("/{id}")
  public ApiResponse<String> updateDocumentTitle(@PathVariable Long id, @Valid @RequestBody UpdateDocumentRequest request) {
    return documentService.updateDocumentTitle(id, request);
  }

  @PatchMapping("/{id}/snapshot")
  public ApiResponse<String> updateDocumentSnapshot(@PathVariable Long id, @RequestBody UpdateDocumentRequest request) {
    return documentService.updateDocumentSnapshot(id, request);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<String> deleteDocument(@PathVariable Long id) {
    return documentService.deleteDocument(id);
  }
}
