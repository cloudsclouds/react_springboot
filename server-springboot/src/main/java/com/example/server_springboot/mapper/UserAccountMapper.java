package com.example.server_springboot.mapper;

import com.example.server_springboot.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {
  UserAccount findByEmail(@Param("email") String email);
}