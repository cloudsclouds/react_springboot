package com.example.server_springboot.controller;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.ApplyShareLinkRequest;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.CreateShareLinkRequest;
import com.example.server_springboot.dto.DocumentMemberResponse;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.ShareLinkResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.dto.UpsertDocumentMemberRequest;
import com.example.server_springboot.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<ApiResponse<DocumentResponse>> getDocumentMetadata(@PathVariable Long id) {
    ApiResponse<DocumentResponse> response = documentService.getDocumentMetadata(id);
    if (!response.isSuccess()) {
      String message = response.getMessage() == null ? "" : response.getMessage();

      if (message.contains("未认证")) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
      }
      if (message.contains("无权限") || message.contains("没有权限") || message.contains("仅创建者")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
      }
      if (message.contains("不存在") || message.contains("已被删除")) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
      }
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    return ResponseEntity.ok(response);
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

  @GetMapping("/{id}/members")
  public ApiResponse<List<DocumentMemberResponse>> getDocumentMembers(@PathVariable Long id) {
    return documentService.getDocumentMembers(id);
  }

  @PostMapping("/{id}/members")
  public ApiResponse<DocumentMemberResponse> upsertDocumentMember(@PathVariable Long id, @Valid @RequestBody UpsertDocumentMemberRequest request) {
    return documentService.upsertDocumentMember(id, request);
  }

  @DeleteMapping("/{id}/members/{userId}")
  public ApiResponse<String> deleteDocumentMember(@PathVariable Long id, @PathVariable Long userId) {
    return documentService.removeDocumentMember(id, userId);
  }

  @PostMapping("/{id}/share-links")
  public ApiResponse<ShareLinkResponse> createShareLink(@PathVariable Long id, @Valid @RequestBody(required = false) CreateShareLinkRequest request) {
    return documentService.createShareLink(id, request == null ? new CreateShareLinkRequest() : request);
  }

  @PostMapping("/{id}/join-by-link")
  public ApiResponse<DocumentMemberResponse> joinByShareLink(@PathVariable Long id, @Valid @RequestBody ApplyShareLinkRequest request) {
    return documentService.applyShareLink(id, request);
  }
}
