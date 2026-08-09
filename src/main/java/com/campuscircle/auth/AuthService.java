package com.campuscircle.auth;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final LoginRateLimiter loginRateLimiter;
    private final String passwordTimingHash;

    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, TokenStore tokenStore,
                       LoginRateLimiter loginRateLimiter) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.loginRateLimiter = loginRateLimiter;
        this.passwordTimingHash = passwordEncoder.encode("campuscircle-login-timing-only");
    }

    public RegisterResponse register(RegisterRequest request) {
        if (authMapper.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        try {
            authMapper.save(request.username(), encodedPassword, generateDefaultNickname());
        } catch (DuplicateKeyException exception) { // 防止并发情况
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        AuthUser user = authMapper.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "用户注册后查询失败"));

        return new RegisterResponse(user.id(), user.username(), user.nickname());
    }

    public LoginResponse login(LoginRequest request) {
        loginRateLimiter.checkAllowed(request.username());
        AuthUser user = authMapper.findByUsername(request.username()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), passwordTimingHash);
            loginRateLimiter.recordFailure(request.username());
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        if (user.status() != 0) {
            loginRateLimiter.recordFailure(request.username());
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.password())) {
            loginRateLimiter.recordFailure(request.username());
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        loginRateLimiter.clear(request.username());

        TokenSession session = tokenStore.createSession(user.id());
        UserSummary userSummary = new UserSummary(
                user.id(),
                user.username(),
                user.nickname(),
                user.avatarUrl(),
                user.role(),
                !Boolean.TRUE.equals(user.nicknameConfirmed())
        );

        return new LoginResponse(
                session.token(),
                session.expiresIn(),
                session.refreshToken(),
                session.refreshExpiresIn(),
                userSummary
        );
    }

    public void logout(String authorization) {
        logout(authorization, null);
    }

    public void logout(String authorization, String refreshToken) {
        String token = tokenStore.resolveBearerToken(authorization)
                .orElse(null);
        if (token != null) {
            tokenStore.remove(token);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenStore.removeRefreshToken(refreshToken);
        }
        if (token == null && (refreshToken == null || refreshToken.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public TokenSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
        return tokenStore.refreshSession(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效"));
    }

    private String generateDefaultNickname() {
        return "CampusUser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
