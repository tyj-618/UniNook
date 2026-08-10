package com.uninook.auth;

public record RegisterResponse(
        Long userId,
        String username,
        String nickname
) {
}
