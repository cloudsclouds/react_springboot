package com.example.server_springboot.user.mapper;

import com.example.server_springboot.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {
  UserAccount findByEmail(@Param("email") String email);
  UserAccount findById(@Param("id") Long id);

  int insertUser(UserAccount userAccount);
}