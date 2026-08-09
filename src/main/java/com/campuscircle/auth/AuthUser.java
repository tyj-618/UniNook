package com.campuscircle.auth;

public record AuthUser(
        Long id,
        String username,
        String password,
        String nickname,
        Boolean nicknameConfirmed,
        String avatarUrl,
        Integer role,
        Integer status
) {
}
