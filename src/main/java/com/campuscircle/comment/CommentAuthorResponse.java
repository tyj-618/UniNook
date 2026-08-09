package com.campuscircle.comment;

public record CommentAuthorResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String schoolName,
        String campusName
) {
}
