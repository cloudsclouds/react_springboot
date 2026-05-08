package com.example.server_springboot.user.service.impl;

import com.example.server_springboot.util.JwtUtils;
import com.example.server_springboot.user.dto.LoginRequest;
import com.example.server_springboot.user.dto.LoginResponse;
import com.example.server_springboot.user.dto.RegisterCodeRequest;
import com.example.server_springboot.user.dto.RegisterCodeResponse;
import com.example.server_springboot.user.dto.RegisterRequest;
import com.example.server_springboot.user.dto.RegisterResponse;
import com.example.server_springboot.user.entity.UserAccount;
import com.example.server_springboot.user.mapper.UserAccountMapper;
import com.example.server_springboot.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  // Redis Key 前缀：注册验证码，完整 key 形如 auth:register:code:xxx@qq.com
  private static final String REGISTER_CODE_KEY_PREFIX = "auth:register:code:";
  // Redis Key 前缀：发送验证码冷却，完整 key 形如 auth:register:cooldown:xxx@qq.com
  private static final String REGISTER_RATE_LIMIT_KEY_PREFIX = "auth:register:cooldown:";
  // Redis Key 前缀：发送次数计数窗口，完整 key 形如 auth:register:count:xxx@qq.com
  private static final String REGISTER_RATE_COUNT_KEY_PREFIX = "auth:register:count:";

  // 验证码有效期 5 分钟
  private static final Duration CODE_TTL = Duration.ofMinutes(5);
  // 每次发送后 60 秒内不允许再次发送（冷却时间）
  private static final Duration SEND_COOLDOWN_TTL = Duration.ofSeconds(60);
  // 统计发送次数的窗口为 1 小时
  private static final Duration RATE_COUNT_WINDOW = Duration.ofHours(1);
  // 同一邮箱 1 小时内最多发送 10 次验证码
  private static final long MAX_SEND_TIMES_PER_HOUR = 10L;

  // 验证码随机生成器
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserAccountMapper userAccountMapper;
  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public LoginResponse login(LoginRequest request) {
    UserAccount user = userAccountMapper.findByEmail(request.getEmail());

    if (user == null) {
      return new LoginResponse(false, "用户不存在", null, null, null);
    }

    // 当前先做明文比对（生产环境建议使用 BCrypt 哈希校验）
    if (!user.getPassword().equals(request.getPassword())) {
      return new LoginResponse(false, "密码错误", null, null, null);
    }

    String token = JwtUtils.generateToken(user.getId());
    return new LoginResponse(true, "登录成功", token, user.getId(), user.getNickname());
  }

  @Override
  public RegisterCodeResponse generateRegisterCode(RegisterCodeRequest request) {
    String email = normalizeEmail(request.getEmail());

    // 如果 MySQL 已有该邮箱，禁止重复注册
    if (userAccountMapper.findByEmail(email) != null) {
      return new RegisterCodeResponse(false, "该邮箱已注册", null);
    }

    // 冷却 key：存在说明 60 秒内重复请求
    String cooldownKey = REGISTER_RATE_LIMIT_KEY_PREFIX + email;
    // hasKey 为 true 表示仍处于冷却期
    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
      return new RegisterCodeResponse(false, "请求过于频繁，请 60 秒后再试", null);
    }

    // 计数 key：用于 1 小时发送上限
    String countKey = REGISTER_RATE_COUNT_KEY_PREFIX + email;
    // 每次请求先自增一次
    Long count = stringRedisTemplate.opsForValue().increment(countKey);
    // 如果是第一次（值为 1），给计数器设置 1 小时过期
    if (count != null && count == 1L) {
      stringRedisTemplate.expire(countKey, RATE_COUNT_WINDOW);
    }
    // 超过每小时最大次数则拒绝
    if (count != null && count > MAX_SEND_TIMES_PER_HOUR) {
      return new RegisterCodeResponse(false, "该邮箱验证码请求次数过多，请 1 小时后再试", null);
    }

    // 生成 6 位数字验证码，左侧补零（例如 000123）
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    // 验证码 key
    String codeKey = REGISTER_CODE_KEY_PREFIX + email;
    // 写入验证码，TTL=5分钟
    stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
    // 写入冷却标记，TTL=60秒
    stringRedisTemplate.opsForValue().set(cooldownKey, "1", SEND_COOLDOWN_TTL);

    // 开发环境把验证码返回给前端；生产建议改成邮件发送且不回传 code
    return new RegisterCodeResponse(true, "验证码已生成", code);
  }

  @Override
  public RegisterResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.getEmail());
    String code = normalizeText(request.getCode());

    if (userAccountMapper.findByEmail(email) != null) {
      return new RegisterResponse(false, "该邮箱已注册", null);
    }

    // 读取验证码 key
    String codeKey = REGISTER_CODE_KEY_PREFIX + email;
    // 从 Redis 取验证码（过期后会自动为 null）
    String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
    // 没取到，说明没发过或已过期
    if (cachedCode == null || cachedCode.isBlank()) {
      return new RegisterResponse(false, "请先获取验证码", null);
    }

    // 验证码不一致，拒绝注册
    if (!cachedCode.equals(code)) {
      return new RegisterResponse(false, "验证码错误", null);
    }

    UserAccount userAccount = new UserAccount();
    userAccount.setUsername(email);
    userAccount.setEmail(email);
    userAccount.setPassword(normalizeText(request.getPassword()));
    userAccount.setNickname(normalizeText(request.getNickname()));

    int affectedRows = userAccountMapper.insertUser(userAccount);
    if (affectedRows <= 0) {
      return new RegisterResponse(false, "注册失败，请稍后重试", null);
    }

    stringRedisTemplate.delete(codeKey);
    return new RegisterResponse(true, "注册成功", userAccount.getId());
  }

  private String normalizeEmail(String email) {
    return normalizeText(email).toLowerCase();
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }
}