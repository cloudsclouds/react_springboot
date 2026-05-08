package com.example.server_springboot.collabdocument.mapper;

import com.example.server_springboot.collabdocument.entity.DocumentMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMemberMapper {
  int insertMember(DocumentMember member);
  int updateRole(@Param("documentId") Long documentId, @Param("userId") Long userId, @Param("role") String role);
  int deleteMember(@Param("documentId") Long documentId, @Param("userId") Long userId);
  DocumentMember findByDocumentIdAndUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);
  java.util.List<DocumentMember> findByDocumentId(@Param("documentId") Long documentId);
}
