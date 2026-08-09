package com.campuscircle.like;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.common.AfterCommitExecutor;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.event.PostLikedEvent;
import com.campuscircle.exception.BusinessException;
import com.campuscircle.post.HotPostRankStore;
import com.campuscircle.post.PostDetail;
import com.campuscircle.post.PostMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {

    private final CurrentUserService currentUserService;
    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final HotPostRankStore hotPostRankStore;
    private final DomainEventPublisher domainEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;

    public LikeService(CurrentUserService currentUserService, LikeMapper likeMapper, PostMapper postMapper,
                       HotPostRankStore hotPostRankStore, DomainEventPublisher domainEventPublisher,
                       AfterCommitExecutor afterCommitExecutor) {
        this.currentUserService = currentUserService;
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
        this.hotPostRankStore = hotPostRankStore;
        this.domainEventPublisher = domainEventPublisher;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Transactional
    public LikeResponse likePost(Long postId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);

        LikeRecord likeRecord = likeMapper.findByPostIdAndUserId(postId, currentUserId).orElse(null);
        if (likeRecord != null && likeRecord.status() == 0) {
            return new LikeResponse(true, likeMapper.findLikeCount(postId));
        }

        if (likeRecord == null) {
            try {
                likeMapper.saveLike(postId, currentUserId);
            } catch (DuplicateKeyException exception) {
                likeRecord = likeMapper.findByPostIdAndUserId(postId, currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "点赞状态查询失败"));
                if (likeRecord.status() == 0) {
                    return new LikeResponse(true, likeMapper.findLikeCount(postId));
                }
                likeMapper.activateLike(likeRecord.id());
            }
        } else {
            likeMapper.activateLike(likeRecord.id());
        }

        likeMapper.increaseLikeCount(postId);
        PostLikedEvent event = PostLikedEvent.create(postDetail.userId(), currentUserId, postId);
        domainEventPublisher.publishPostLiked(event);
        afterCommitExecutor.execute(() -> hotPostRankStore.increaseScore(
                postId, postDetail.categoryId(), HotPostRankStore.LIKE_SCORE
        ));
        return new LikeResponse(true, likeMapper.findLikeCount(postId));
    }

    @Transactional
    public LikeResponse unlikePost(Long postId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);

        LikeRecord likeRecord = likeMapper.findByPostIdAndUserId(postId, currentUserId).orElse(null);
        if (likeRecord == null || likeRecord.status() == 1) {
            return new LikeResponse(false, likeMapper.findLikeCount(postId));
        }

        likeMapper.cancelLike(likeRecord.id());
        likeMapper.decreaseLikeCount(postId);
        afterCommitExecutor.execute(() -> hotPostRankStore.decreaseScore(
                postId, postDetail.categoryId(), HotPostRankStore.LIKE_SCORE
        ));
        return new LikeResponse(false, likeMapper.findLikeCount(postId));
    }

    public LikeStatusResponse getLikeStatus(Long postId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);

        boolean liked = likeMapper.findByPostIdAndUserId(postId, currentUserId)
                .map(likeRecord -> likeRecord.status() == 0)
                .orElse(false);

        return new LikeStatusResponse(liked);
    }

    private PostDetail findNormalPost(Long postId) {
        PostDetail postDetail = postMapper.findDetailById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (postDetail.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        return postDetail;
    }
}
