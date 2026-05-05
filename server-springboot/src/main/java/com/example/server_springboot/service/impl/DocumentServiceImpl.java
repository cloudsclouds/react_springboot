package com.example.server_springboot.service.impl;

import com.example.server_springboot.context.UserContext;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.ApplyShareLinkRequest;
import com.example.server_springboot.dto.CreateDocumentRequest;
import com.example.server_springboot.dto.CreateShareLinkRequest;
import com.example.server_springboot.dto.DocumentMemberResponse;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.ShareLinkResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.dto.UpsertDocumentMemberRequest;
import com.example.server_springboot.entity.Document;
import com.example.server_springboot.entity.DocumentMember;
import com.example.server_springboot.entity.ShareLink;
import com.example.server_springboot.mapper.DocumentMapper;
import com.example.server_springboot.mapper.DocumentMemberMapper;
import com.example.server_springboot.mapper.ShareLinkMapper;
import com.example.server_springboot.mapper.UserAccountMapper;
import com.example.server_springboot.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

  private final DocumentMapper documentMapper;
  private final DocumentMemberMapper documentMemberMapper;
  private final ShareLinkMapper shareLinkMapper;
  private final UserAccountMapper userAccountMapper;

  @Override
  @Transactional
  public ApiResponse<Map<String, Long>> createDocument(CreateDocumentRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    Document doc = new Document();
    doc.setTitle(request.getTitle());
    doc.setOwnerId(userId);
    String ownerName = getCurrentUserName(userId);
    doc.setOwnerName(ownerName);
    doc.setLatestSnapshot(buildInitialSnapshot(request.getTitle(), ownerName));

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
    list = list.stream()
      .filter(document -> {
        String role = document.getMyRole();
        return "owner".equalsIgnoreCase(role) || "editor".equalsIgnoreCase(role) || "viewer".equalsIgnoreCase(role);
      })
      .toList();
    return ApiResponse.success("获取成功", list);
  }

  @Override
  public ApiResponse<DocumentResponse> getDocumentMetadata(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    return buildDocumentResponse(id, userId, false);
  }

  @Override
  public ApiResponse<DocumentResponse> getDocumentMetadataForInternal(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    return buildDocumentResponse(id, userId, true);
  }

  @Override
  public ApiResponse<DocumentResponse> getDocumentMetadataForUser(Long id, Long userId) {
    if (userId == null) return ApiResponse.error("未认证用户");
    return buildDocumentResponse(id, userId, false);
  }

  @Override
  @Transactional
  public ApiResponse<String> updateDocumentTitle(Long id, UpdateDocumentRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "editor");
    if (access == null) {
      return ApiResponse.error("无权限修改文档");
    }

    documentMapper.updateTitle(id, request.getTitle());
    return ApiResponse.success("修改成功", null);
  }

  @Override
  @Transactional
  public ApiResponse<String> updateDocumentSnapshot(Long id, UpdateDocumentRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "editor");
    if (access == null) {
      return ApiResponse.error("无权限修改文档内容");
    }

    String snapshot = request.getLatestSnapshot();
    if (snapshot == null || snapshot.isBlank()) {
      return ApiResponse.error("文档内容不能为空");
    }

    documentMapper.updateSnapshot(id, snapshot);
    return ApiResponse.success("内容已保存", null);
  }

  @Override
  public ApiResponse<List<DocumentMemberResponse>> getDocumentMembers(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "viewer");
    if (access == null) {
      return ApiResponse.error("无权限访问该文档");
    }

    List<DocumentMemberResponse> members = documentMemberMapper.findByDocumentId(id).stream().map(member -> {
      DocumentMemberResponse response = new DocumentMemberResponse();
      response.setUserId(member.getUserId());
      response.setRole(member.getRole());
      response.setJoinedAt(member.getJoinedAt());
      response.setNickname(getCurrentUserName(member.getUserId()));
      response.setEditable(hasPermission(member.getRole(), "editor"));
      return response;
    }).filter(member -> hasPermission(member.getRole(), "viewer")).toList();

    return ApiResponse.success("获取成功", members);
  }

  @Override
  @Transactional
  public ApiResponse<DocumentMemberResponse> upsertDocumentMember(Long id, UpsertDocumentMemberRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "owner");
    if (access == null) {
      return ApiResponse.error("无权限管理成员");
    }

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, request.getUserId());
    if (member == null) {
      DocumentMember newMember = new DocumentMember();
      newMember.setDocumentId(id);
      newMember.setUserId(request.getUserId());
      newMember.setRole(request.getRole());
      documentMemberMapper.insertMember(newMember);
      member = newMember;
    } else {
      documentMemberMapper.updateRole(id, request.getUserId(), request.getRole());
      member.setRole(request.getRole());
    }

    DocumentMemberResponse response = new DocumentMemberResponse();
    response.setUserId(member.getUserId());
    response.setRole(member.getRole());
    response.setJoinedAt(member.getJoinedAt());
    response.setNickname(getCurrentUserName(member.getUserId()));
    response.setEditable(hasPermission(member.getRole(), "editor"));
    response.setCommentable(hasPermission(member.getRole(), "commentor"));
    return ApiResponse.success("保存成功", response);
  }

  @Override
  @Transactional
  public ApiResponse<String> removeDocumentMember(Long id, Long targetUserId) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "owner");
    if (access == null) {
      return ApiResponse.error("无权限管理成员");
    }

    if (userId.equals(targetUserId)) {
      return ApiResponse.error("不能移除自己");
    }

    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return ApiResponse.error("文档不存在或已被删除");
    }
    if (doc.getOwnerId() != null && doc.getOwnerId().equals(targetUserId)) {
      return ApiResponse.error("不能移除文档拥有者");
    }

    documentMemberMapper.deleteMember(id, targetUserId);
    return ApiResponse.success("移除成功", null);
  }

  @Override
  @Transactional
  public ApiResponse<ShareLinkResponse> createShareLink(Long id, CreateShareLinkRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "owner");
    if (access == null) {
      return ApiResponse.error("无权限管理成员");
    }

    String permission = request == null || request.getPermission() == null || request.getPermission().isBlank()
      ? "viewer"
      : request.getPermission().toLowerCase();
    if (!"viewer".equals(permission) && !"editor".equals(permission)) {
      return ApiResponse.error("分享权限无效");
    }

    shareLinkMapper.deleteByDocumentId(id);

    ShareLink shareLink = new ShareLink();
    shareLink.setDocumentId(id);
    shareLink.setPermission(permission);
    shareLink.setShareToken(UUID.randomUUID().toString().replace("-", ""));
    shareLink.setExpireTime(LocalDateTime.now().plusDays(7));
    shareLinkMapper.insert(shareLink);

    ShareLinkResponse response = new ShareLinkResponse();
    response.setDocumentId(id);
    response.setShareToken(shareLink.getShareToken());
    response.setPermission(permission);
    response.setExpireTime(shareLink.getExpireTime());
    response.setShareUrl("/documents/" + id + "?shareToken=" + shareLink.getShareToken());

    return ApiResponse.success("生成成功", response);
  }

  @Override
  @Transactional
  public ApiResponse<DocumentMemberResponse> applyShareLink(Long id, ApplyShareLinkRequest request) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    ShareLink shareLink = shareLinkMapper.findByShareToken(request.getShareToken());
    if (shareLink == null || !id.equals(shareLink.getDocumentId())) {
      return ApiResponse.error("分享链接无效");
    }
    if (shareLink.getExpireTime() != null && shareLink.getExpireTime().isBefore(LocalDateTime.now())) {
      return ApiResponse.error("分享链接已过期");
    }

    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return ApiResponse.error("文档不存在或已被删除");
    }

    if (doc.getOwnerId() != null && doc.getOwnerId().equals(userId)) {
      DocumentMemberResponse ownerResponse = new DocumentMemberResponse();
      ownerResponse.setUserId(userId);
      ownerResponse.setRole("owner");
      ownerResponse.setNickname(getCurrentUserName(userId));
      ownerResponse.setEditable(true);
      return ApiResponse.success("你已是文档拥有者", ownerResponse);
    }

    String role = shareLink.getPermission();
    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null) {
      member = new DocumentMember();
      member.setDocumentId(id);
      member.setUserId(userId);
      member.setRole(role);
      documentMemberMapper.insertMember(member);
    } else {
      documentMemberMapper.updateRole(id, userId, role);
      member.setRole(role);
    }

    DocumentMemberResponse response = new DocumentMemberResponse();
    response.setUserId(userId);
    response.setRole(role);
    response.setNickname(getCurrentUserName(userId));
    response.setJoinedAt(member.getJoinedAt());
    response.setEditable(hasPermission(role, "editor"));
    return ApiResponse.success("加入成功", response);
  }

  @Override
  @Transactional
  public ApiResponse<String> deleteDocument(Long id) {
    Long userId = UserContext.getUserId();
    if (userId == null) return ApiResponse.error("未认证用户");

    DocumentResponse access = requireDocumentAccess(id, userId, "owner");
    if (access == null) {
      return ApiResponse.error("仅创建者可删除文档");
    }

    documentMapper.updateStatus(id, 1);
    return ApiResponse.success("删除成功", null);
  }

  private ApiResponse<DocumentResponse> buildDocumentResponse(Long id, Long userId, boolean allowMissingMember) {
    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return ApiResponse.error("文档不存在或已被删除");
    }

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null) {
      return ApiResponse.error("无权限访问该文档");
    }

    String myRole = member.getRole() == null ? "" : member.getRole().toLowerCase();
    if ("no_access".equals(myRole)) {
      return ApiResponse.error("无权限访问该文档");
    }

    DocumentResponse resp = new DocumentResponse();
    resp.setId(doc.getId());
    resp.setTitle(doc.getTitle());
    resp.setOwnerId(doc.getOwnerId());
    resp.setOwnerName(doc.getOwnerName());
    resp.setLatestSnapshot(doc.getLatestSnapshot());
    resp.setMyRole(member.getRole());
    resp.setUpdatedAt(doc.getUpdatedAt());

    return ApiResponse.success("获取成功", resp);
  }

  private DocumentResponse requireDocumentAccess(Long id, Long userId, String requiredRole) {
    Document doc = documentMapper.findById(id);
    if (doc == null || doc.getStatus() == 1) {
      return null;
    }

    DocumentMember member = documentMemberMapper.findByDocumentIdAndUserId(id, userId);
    if (member == null) {
      return null;
    }

    if (!hasPermission(member.getRole(), requiredRole)) {
      return null;
    }

    DocumentResponse resp = new DocumentResponse();
    resp.setId(doc.getId());
    resp.setTitle(doc.getTitle());
    resp.setOwnerId(doc.getOwnerId());
    resp.setOwnerName(doc.getOwnerName());
    resp.setLatestSnapshot(doc.getLatestSnapshot());
    resp.setMyRole(member.getRole());
    resp.setUpdatedAt(doc.getUpdatedAt());
    return resp;
  }

  private boolean hasPermission(String role, String requiredRole) {
    return roleRank(role) >= roleRank(requiredRole);
  }

  private int roleRank(String role) {
    return switch (role == null ? "" : role.toLowerCase()) {
      case "owner" -> 4;
      case "editor" -> 3;
      case "no_access" -> 0;
      case "viewer" -> 1;
      default -> 0;
    };
  }

  private String getCurrentUserName(Long userId) {
    var user = userAccountMapper.findById(userId);
    if (user == null) {
      return "未知作者";
    }
    return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
  }

  private String buildInitialSnapshot(String title, String ownerName) {
    return "{"
      + "\"type\":\"doc\"," 
      + "\"version\":1,"
      + "\"title\":\"" + escapeJson(title) + "\","
      + "\"content\":["
      + "{\"type\":\"heading\",\"attrs\":{\"level\":1},\"content\":[{\"type\":\"text\",\"text\":\"" + escapeJson(title) + "\"}]},"
      + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"作者：" + escapeJson(ownerName) + "\"}]},"
      + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"用途：用于测试文档标题、作者、正文快照与协同加载。\"}]},"
      + "{\"type\":\"bulletList\",\"content\":["
      + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"支持标题编辑\"}]}]},"
      + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"支持作者信息展示\"}]}]},"
      + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"支持快照加载测试\"}]}]}]"
      + "}]"
      + "}";
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
