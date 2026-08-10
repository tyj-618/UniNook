package com.uninook.comment;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.AfterCommitExecutor;
import com.uninook.common.ErrorCode;
import com.uninook.common.PageResponse;
import com.uninook.event.CommentCreatedEvent;
import com.uninook.event.DomainEventPublisher;
import com.uninook.exception.BusinessException;
import com.uninook.post.HotPostRankStore;
import com.uninook.post.PageQueryResult;
import com.uninook.post.PostDetail;
import com.uninook.post.PostMapper;
import com.uninook.question.QuestionAnswerService;
import com.uninook.user.UserProfile;
import com.uninook.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CurrentUserService currentUserService;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final HotPostRankStore hotPostRankStore;
    private final DomainEventPublisher domainEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final QuestionAnswerService questionAnswerService;

    public CommentService(CurrentUserService currentUserService, CommentMapper commentMapper, UserMapper userMapper,
                          PostMapper postMapper, HotPostRankStore hotPostRankStore, DomainEventPublisher domainEventPublisher,
                          AfterCommitExecutor afterCommitExecutor, QuestionAnswerService questionAnswerService) {
        this.currentUserService = currentUserService;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.hotPostRankStore = hotPostRankStore;
        this.domainEventPublisher = domainEventPublisher;
        this.afterCommitExecutor = afterCommitExecutor;
        this.questionAnswerService = questionAnswerService;
    }

    @Transactional
    public CreateCommentResponse createComment(Long postId, String authorization, Long clientUserId,
                                               double radiusKm, CreateCommentRequest request) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        ensureClientUserMatchesToken(clientUserId, currentUserId);
        UserProfile currentUser = userMapper.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Long authorSchoolId = currentUser.requireSchoolId();
        PostDetail postDetail = findNormalPost(postId);

        CommentDetail parentComment = resolveParentComment(postId, request.parentCommentId());
        Long rootCommentId = parentComment == null ? null
                : parentComment.rootCommentId() == null ? parentComment.id() : parentComment.rootCommentId();
        Long parentCommentId = parentComment == null ? null : parentComment.id();
        // A follow-up on a top-level comment stays a normal thread comment. Only replying
        // to an existing second-level comment is rendered as an explicit reply.
        Long replyToUserId = parentComment == null || parentComment.rootCommentId() == null
                ? null : parentComment.userId();

        Long commentId = commentMapper.saveComment(
                postId, currentUserId, authorSchoolId, currentUser.schoolName(), currentUser.campusName(),
                rootCommentId, parentCommentId, replyToUserId, request.content()
        );
        if (commentId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "评论创建失败");
        }

        if (request.answerQuestionId() != null) {
            questionAnswerService.registerCandidate(request.answerQuestionId(), commentId, currentUserId, postId, parentCommentId);
        }

        commentMapper.increaseCommentCount(postId);
        Long noticeReceiverId = replyToUserId == null
                ? parentComment == null ? postDetail.userId() : parentComment.userId()
                : replyToUserId;
        if (request.answerQuestionId() == null && !currentUserId.equals(noticeReceiverId)) {
            CommentCreatedEvent event = CommentCreatedEvent.create(noticeReceiverId, currentUserId, postId, commentId);
            domainEventPublisher.publishCommentCreated(event);
        }
        afterCommitExecutor.execute(() -> hotPostRankStore.increaseScore(
                postId, postDetail.categoryId(), HotPostRankStore.COMMENT_SCORE
        ));
        return new CreateCommentResponse(commentId);
    }

    public PageResponse<CommentResponse> listPostComments(Long postId, String authorization, int page, int size,
                                                           Long focusCommentId, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);

        PageQueryResult<CommentPageItem> result = focusCommentId == null
                ? commentMapper.findCommentsByPostId(postId, currentUserId, page, size)
                : commentMapper.findCommentThreadByTarget(postId, currentUserId, focusCommentId);
        List<CommentResponse> records = result.records().stream()
                .map(CommentResponse::from)
                .toList();

        return PageResponse.of(page, size, result.total(), records);
    }

    @Transactional
    public void deleteComment(Long commentId, String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        CommentDetail commentDetail = findNormalComment(commentId);
        ensureCanDeleteComment(currentUserId, commentDetail);

        boolean isTopLevelComment = commentDetail.rootCommentId() == null;
        long deletedCount = isTopLevelComment
                ? commentMapper.countNormalCommentThread(commentDetail.id())
                : 1;
        if (isTopLevelComment) {
            questionAnswerService.withdrawCommentThreadCandidates(commentDetail.id());
            commentMapper.softDeleteCommentThread(commentDetail.id());
        } else {
            questionAnswerService.withdrawCommentCandidate(commentDetail.id());
            commentMapper.softDeleteComment(commentDetail.id());
        }
        commentMapper.decreaseCommentCount(commentDetail.postId(), deletedCount);
        postMapper.findDetailById(commentDetail.postId())
                .filter(postDetail -> postDetail.status() == 0)
                .ifPresent(postDetail -> afterCommitExecutor.execute(() -> hotPostRankStore.decreaseScore(
                        commentDetail.postId(), postDetail.categoryId(), HotPostRankStore.COMMENT_SCORE * deletedCount
                )));
    }

    public PageResponse<MyCommentResponse> listMyComments(String authorization, int page, int size) {
        Long currentUserId = currentUserService.requireUserId(authorization);

        PageQueryResult<MyCommentItem> result = commentMapper.findCommentsByUserId(currentUserId, page, size);
        List<MyCommentResponse> records = result.records().stream()
                .map(MyCommentResponse::from)
                .toList();

        return PageResponse.of(page, size, result.total(), records);
    }

    private PostDetail findNormalPost(Long postId) {
        PostDetail postDetail = postMapper.findDetailById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (postDetail.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        return postDetail;
    }

    private CommentDetail findNormalComment(Long commentId) {
        CommentDetail commentDetail = commentMapper.findDetailById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论不存在"));

        if (commentDetail.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }

        return commentDetail;
    }

    private CommentDetail resolveParentComment(Long postId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }

        CommentDetail parentComment = findNormalComment(parentCommentId);
        if (!postId.equals(parentComment.postId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "回复目标不属于当前帖子");
        }
        return parentComment;
    }

    private void ensureCanDeleteComment(Long currentUserId, CommentDetail commentDetail) {
        if (currentUserId.equals(commentDetail.userId()) || currentUserId.equals(commentDetail.postAuthorId())) {
            return;
        }

        UserProfile currentUser = userMapper.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (currentUser.role() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论或自己帖子下的评论");
        }
    }

    private void ensureClientUserMatchesToken(Long clientUserId, Long currentUserId) {
        if (clientUserId != null && !clientUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前账号会话已变化，请重新登录后再发布评论");
        }
    }
}
