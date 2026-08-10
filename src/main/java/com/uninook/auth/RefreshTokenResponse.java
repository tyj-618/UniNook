package com.uninook.auth;

public record RefreshTokenResponse(
        String token,
        long expiresIn
) {
}
