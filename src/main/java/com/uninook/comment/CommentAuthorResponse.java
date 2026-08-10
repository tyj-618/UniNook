package com.uninook.comment;

public record CommentAuthorResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String schoolName,
        String campusName
) {
}
