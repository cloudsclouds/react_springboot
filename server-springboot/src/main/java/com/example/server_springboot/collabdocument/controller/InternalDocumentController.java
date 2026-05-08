package com.example.server_springboot.collabdocument.controller;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.collabdocument.dto.DocumentResponse;
import com.example.server_springboot.collabdocument.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/docs")
@RequiredArgsConstructor
public class InternalDocumentController {

  private final DocumentService documentService;

  @Value("${app.internal-secret:paperdesk-internal-secret}")
  private String internalSecret;

  @GetMapping("/{id}/snapshot")
  public ApiResponse<DocumentResponse> getDocumentSnapshot(@PathVariable Long id, @RequestHeader("X-Internal-Secret") String secret) {
    if (!internalSecret.equals(secret)) {
      return ApiResponse.error("Unauthorized internal request");
    }
    return documentService.getDocumentMetadataForInternal(id);
  }
}
