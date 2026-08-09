package com.campuscircle.comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        Long rootCommentId,
        Long parentCommentId,
        CommentAuthorResponse author,
        Long replyToUserId,
        String replyToNickname,
        int likeCount,
        boolean liked,
        LocalDateTime createdAt
) {

    public static CommentResponse from(CommentPageItem item) {
        return new CommentResponse(
                item.id(),
                item.content(),
                item.rootCommentId(),
                item.parentCommentId(),
                new CommentAuthorResponse(item.userId(), item.authorNickname(), item.authorAvatarUrl(),
                        item.authorSchoolName(), item.authorCampusName()),
                item.replyToUserId(),
                item.replyToNickname(),
                item.likeCount() == null ? 0 : item.likeCount(),
                Boolean.TRUE.equals(item.liked()),
                item.createdAt()
        );
    }
}
