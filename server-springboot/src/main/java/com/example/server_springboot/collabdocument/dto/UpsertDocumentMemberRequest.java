package com.example.server_springboot.collabdocument.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpsertDocumentMemberRequest {
  @NotNull(message = "userId 不能为空")
  private Long userId;

  @NotNull(message = "role 不能为空")
  @Pattern(regexp = "owner|editor|viewer|no_access", message = "角色类型无效")
  private String role;
}
