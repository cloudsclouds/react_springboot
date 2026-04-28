package com.example.server_springboot.service.impl;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.entity.Document;
import com.example.server_springboot.entity.DocumentMember;
import com.example.server_springboot.mapper.DocumentMapper;
import com.example.server_springboot.mapper.DocumentMemberMapper;
import com.example.server_springboot.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

  private final DocumentMapper documentMapper;
  private final DocumentMemberMapper documentMemberMapper;

  @Override
  @Transactional
  public ApiResponse<Map<String, Long>> createDocument(CreateDocumentRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    Document doc = new Document();
    doc.setTitle(request.getTitle());
    doc.setOwnerId(userId);
    doc.setLatestSnapshot("{}"); 
    
    documentMapper.insertDocument(doc);

    DocumentMember member = new DocumentMember();
    member.setDocumentId(doc.getId());
    member.setUserId(userId);
    member.setRole("owner");
    documentMemberMapper.insertMember(member);

    Map<String, Long> data = new HashMap<>();
    data.put("documentId", doc.getId());
    return ApiResponse.success("创建成功", data);
  }

  @Override
  public ApiResponse<List<DocumentResponse>> getDocumentList() {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    List<DocumentResponse> list = documentMapper.findUserDocuments(userId);
    return ApiResponse.success("获取成功", list);
  }

  @Override
  public ApiResponse<DocumentResponse> getDocumentMetadata(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return ApiResponse.error("文档不存在或已被删除");
    }

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null) {
      return ApiResponse.error("无权限访问该文档");
    }

    DocumentResponse resp = new DocumentResponse();
    resp.setId(doc.getId());
    resp.setTitle(doc.getTitle());
    resp.setOwnerId(doc.getOwnerId());
    resp.setMyRole(member.getRole());
    resp.setUpdatedAt(doc.getUpdatedAt());

    return ApiResponse.success("获取成功", resp);
  }

  @Override
  @Transactional
  public ApiResponse<String> updateDocumentTitle(Long id, UpdateDocumentRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null || (!"owner".equals(member.getRole()) && !"editor".equals(member.getRole()))) {
      return ApiResponse.error("无权限修改文档");
    }

    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return ApiResponse.error("文档不存在或已被删除");
    }

    documentMapper.updateTitle(id, request.getTitle());
    return ApiResponse.success("修改成功", null);
  }

  @Override
  @Transactional
  public ApiResponse<String> deleteDocument(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null || !"owner".equals(member.getRole())) {
      return ApiResponse.error("仅创建者可删除文档");
    }

    Document doc = documentMapper.findById(id);
    if (doc == null) {
      return ApiResponse.error("文档不存在");
    }

    documentMapper.updateStatus(id, 1);
    return ApiResponse.success("删除成功", null);
  }
}
