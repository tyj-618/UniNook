package com.campuscircle.auth;

public record RefreshTokenResponse(
        String token,
        long expiresIn
) {
}
