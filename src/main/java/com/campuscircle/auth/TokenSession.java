package com.campuscircle.auth;

public record TokenSession(
        String token,
        String refreshToken,
        Long userId,
        long expiresIn,
        long refreshExpiresIn
) {
}
