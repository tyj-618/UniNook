package com.uninook.auth;

public record TokenSession(
        String token,
        String refreshToken,
        Long userId,
        long expiresIn,
        long refreshExpiresIn
) {
}
