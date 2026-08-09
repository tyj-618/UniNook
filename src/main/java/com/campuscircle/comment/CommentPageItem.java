package com.campuscircle.comment;

import java.time.LocalDateTime;

public record CommentPageItem(
        Long id,
        Long postId,
        Long userId,
        Long rootCommentId,
        Long parentCommentId,
        Long replyToUserId,
        String content,
        Integer likeCount,
        String authorNickname,
        String authorAvatarUrl,
        String authorSchoolName,
        String authorCampusName,
        String replyToNickname,
        Boolean liked,
        LocalDateTime createdAt
) {
}
