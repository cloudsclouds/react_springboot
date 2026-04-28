package com.example.server_springboot.mapper;

import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DocumentMapper {
  int insertDocument(Document document);
  Document findById(@Param("id") Long id);
  List<DocumentResponse> findUserDocuments(@Param("userId") Long userId);
  int updateTitle(@Param("id") Long id, @Param("title") String title);
  int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
