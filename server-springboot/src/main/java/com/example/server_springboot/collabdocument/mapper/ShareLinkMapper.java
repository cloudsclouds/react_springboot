package com.example.server_springboot.collabdocument.mapper;

import com.example.server_springboot.collabdocument.entity.ShareLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShareLinkMapper {
  int insert(ShareLink shareLink);
  ShareLink findByDocumentId(@Param("documentId") Long documentId);
  ShareLink findByShareToken(@Param("shareToken") String shareToken);
  int deleteByDocumentId(@Param("documentId") Long documentId);
}
