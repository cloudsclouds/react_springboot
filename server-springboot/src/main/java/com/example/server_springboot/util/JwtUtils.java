package com.example.server_springboot.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtUtils {

  private static final String SECRET_KEY = "my-super-secret-jwt-key"; // In production, move to properties
  private static final long EXPIRATION_TIME = 86400000; // 24 hours in ms

  public static String generateToken(Long userId) {
    return JWT.create()
        .withClaim("userId", userId)
        .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
        .sign(Algorithm.HMAC256(SECRET_KEY));
  }

  public static Long verifyToken(String token) {
    try {
      DecodedJWT jwt = JWT.require(Algorithm.HMAC256(SECRET_KEY))
          .build()
          .verify(token);
      return jwt.getClaim("userId").asLong();
    } catch (JWTVerificationException e) {
      return null;
    }
  }
}
