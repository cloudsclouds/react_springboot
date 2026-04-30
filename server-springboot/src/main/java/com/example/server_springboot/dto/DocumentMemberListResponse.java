package com.example.server_springboot.dto;

import lombok.Data;
import java.util.List;

@Data
public class DocumentMemberListResponse {
  private Long documentId;
  private List<DocumentMemberResponse> members;
}
