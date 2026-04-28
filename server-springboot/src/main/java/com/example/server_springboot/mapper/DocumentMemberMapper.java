package com.example.server_springboot.mapper;

import com.example.server_springboot.entity.DocumentMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMemberMapper {
  int insertMember(DocumentMember member);
  DocumentMember findByDocumentIdAndUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);
}
