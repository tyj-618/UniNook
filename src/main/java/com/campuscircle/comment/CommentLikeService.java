package com.campuscircle.comment;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.event.CommentLikedEvent;
import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.exception.BusinessException;
import com.campuscircle.post.PostDetail;
import com.campuscircle.post.PostMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentLikeService {

    private final CurrentUserService currentUserService;
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostMapper postMapper;
    private final DomainEventPublisher domainEventPublisher;

    public CommentLikeService(CurrentUserService currentUserService, CommentMapper commentMapper,
                              CommentLikeMapper commentLikeMapper, PostMapper postMapper,
                              DomainEventPublisher domainEventPublisher) {
        this.currentUserService = currentUserService;
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.postMapper = postMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CommentLikeResponse likeComment(Long commentId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        CommentDetail comment = findNormalComment(commentId);
        CommentLikeRecord likeRecord = commentLikeMapper.findByCommentIdAndUserId(commentId, currentUserId).orElse(null);
        if (likeRecord != null && likeRecord.status() == 0) {
            return new CommentLikeResponse(true, commentLikeMapper.findLikeCount(commentId));
        }

        if (likeRecord == null) {
            try {
                commentLikeMapper.saveLike(commentId, currentUserId);
            } catch (DuplicateKeyException exception) {
                likeRecord = commentLikeMapper.findByCommentIdAndUserId(commentId, currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Comment like status lookup failed"));
                if (likeRecord.status() == 0) {
                    return new CommentLikeResponse(true, commentLikeMapper.findLikeCount(commentId));
                }
                commentLikeMapper.activateLike(likeRecord.id());
            }
        } else {
            commentLikeMapper.activateLike(likeRecord.id());
        }

        commentLikeMapper.increaseLikeCount(commentId);
        if (!currentUserId.equals(comment.userId())) {
            domainEventPublisher.publishCommentLiked(CommentLikedEvent.create(comment.userId(), currentUserId, comment.postId(), commentId));
        }
        return new CommentLikeResponse(true, commentLikeMapper.findLikeCount(commentId));
    }

    @Transactional
    public CommentLikeResponse unlikeComment(Long commentId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        findNormalComment(commentId);
        CommentLikeRecord likeRecord = commentLikeMapper.findByCommentIdAndUserId(commentId, currentUserId).orElse(null);
        if (likeRecord == null || likeRecord.status() == 1) {
            return new CommentLikeResponse(false, commentLikeMapper.findLikeCount(commentId));
        }
        commentLikeMapper.cancelLike(likeRecord.id());
        commentLikeMapper.decreaseLikeCount(commentId);
        return new CommentLikeResponse(false, commentLikeMapper.findLikeCount(commentId));
    }

    private CommentDetail findNormalComment(Long commentId) {
        CommentDetail comment = commentMapper.findDetailById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Comment not found"));
        if (comment.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Comment not found");
        }
        PostDetail post = postMapper.findDetailById(comment.postId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Post not found"));
        if (post.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Post not found");
        }
        return comment;
    }
}
