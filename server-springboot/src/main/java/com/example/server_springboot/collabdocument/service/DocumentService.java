package com.example.server_springboot.collabdocument.service;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.collabdocument.dto.CreateDocumentRequest;
import com.example.server_springboot.collabdocument.dto.ApplyShareLinkRequest;
import com.example.server_springboot.collabdocument.dto.CreateShareLinkRequest;
import com.example.server_springboot.collabdocument.dto.DocumentMemberResponse;
import com.example.server_springboot.collabdocument.dto.DocumentResponse;
import com.example.server_springboot.collabdocument.dto.ShareLinkResponse;
import com.example.server_springboot.collabdocument.dto.UpdateDocumentRequest;
import com.example.server_springboot.collabdocument.dto.UpsertDocumentMemberRequest;
import java.util.List;
import java.util.Map;

public interface DocumentService {
  ApiResponse<Map<String, Long>> createDocument(CreateDocumentRequest request);
  ApiResponse<List<DocumentResponse>> getDocumentList();
  ApiResponse<DocumentResponse> getDocumentMetadata(Long id);
  ApiResponse<DocumentResponse> getDocumentMetadataForUser(Long id, Long userId);
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
