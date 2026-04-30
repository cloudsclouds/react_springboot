package com.example.server_springboot.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.ApplyShareLinkRequest;
import com.example.server_springboot.dto.CreateShareLinkRequest;
import com.example.server_springboot.dto.DocumentMemberResponse;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.ShareLinkResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.dto.UpsertDocumentMemberRequest;
import java.util.List;
import java.util.Map;

public interface DocumentService {
  ApiResponse<Map<String, Long>> createDocument(CreateDocumentRequest request);
  ApiResponse<List<DocumentResponse>> getDocumentList();
  ApiResponse<DocumentResponse> getDocumentMetadata(Long id);
  ApiResponse<String> updateDocumentTitle(Long id, UpdateDocumentRequest request);
  ApiResponse<String> updateDocumentSnapshot(Long id, UpdateDocumentRequest request);
  ApiResponse<String> deleteDocument(Long id);
  ApiResponse<DocumentResponse> getDocumentMetadataForInternal(Long id);
  ApiResponse<List<DocumentMemberResponse>> getDocumentMembers(Long id);
  ApiResponse<DocumentMemberResponse> upsertDocumentMember(Long id, UpsertDocumentMemberRequest request);
  ApiResponse<String> removeDocumentMember(Long id, Long userId);
  ApiResponse<ShareLinkResponse> createShareLink(Long id, CreateShareLinkRequest request);
  ApiResponse<DocumentMemberResponse> applyShareLink(Long id, ApplyShareLinkRequest request);
}
