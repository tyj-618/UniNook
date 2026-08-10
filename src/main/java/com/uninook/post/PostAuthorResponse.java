package com.uninook.post;

public record PostAuthorResponse(
        Long id,
        String nickname,
        String avatarUrl
) {
}
