package com.campuscircle.auth;

public record RegisterResponse(
        Long userId,
        String username,
        String nickname
) {
}
