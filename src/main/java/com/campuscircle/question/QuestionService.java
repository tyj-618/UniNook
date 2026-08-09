package com.campuscircle.question;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.comment.CommentDetail;
import com.campuscircle.comment.CommentMapper;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.common.PageResponse;
import com.campuscircle.common.entity.QuestionEntity;
import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.event.QuestionLifecycleEvent;
import com.campuscircle.exception.BusinessException;
import com.campuscircle.post.PostDetail;
import com.campuscircle.post.PostMapper;
import com.campuscircle.user.UserMapper;
import com.campuscircle.user.UserProfile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private final CurrentUserService currentUserService;
    private final QuestionMapper questionMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final DomainEventPublisher domainEventPublisher;

    public QuestionService(CurrentUserService currentUserService, QuestionMapper questionMapper, PostMapper postMapper,
                           CommentMapper commentMapper, UserMapper userMapper, QuestionAnswerMapper questionAnswerMapper,
                           DomainEventPublisher domainEventPublisher) {
        this.currentUserService = currentUserService;
        this.questionMapper = questionMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public QuestionResponse createQuestion(String authorization, CreateQuestionRequest request) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        SourceNode source = resolveNormalSource(request.sourceType(), request.sourceId());
        ensureCanManageSource(currentUserId, source.authorId());
        if (questionMapper.existsBySource(request.sourceType(), request.sourceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "每篇帖子或每条评论仅可发起一个问题追踪。");
        }

        QuestionEntity question = new QuestionEntity();
        question.setSourceType(request.sourceType().name());
        question.setSourceId(request.sourceId());
        question.setAskerId(source.authorId());
        question.setQuestionText(request.questionText().strip());
        question.setStatus(QuestionStatus.OPEN.name());
        question.setSubscriberCount(0L);
        try {
            questionMapper.insert(question);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "每篇帖子或每条评论仅可发起一个问题追踪。");
        }
        return findResponse(question.getId(), currentUserId);
    }

    public QuestionResponse findBySource(String authorization, QuestionSourceType sourceType, Long sourceId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        resolveNormalSource(sourceType, sourceId);
        return questionMapper.findBySource(sourceType, sourceId)
                .map(item -> toResponse(item, currentUserId))
                .orElse(null);
    }

    public Map<Long, QuestionSourceSummary> findBySources(String authorization, QuestionSourceType sourceType,
                                                            List<Long> sourceIds) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        List<Long> normalizedIds = sourceIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(50)
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        List<QuestionItem> items = questionMapper.findBySources(sourceType, normalizedIds);
        Set<Long> subscribedQuestionIds = items.isEmpty() ? Set.of() : questionMapper.findSubscribedQuestionIds(
                currentUserId, items.stream().map(QuestionItem::id).toList()).stream().collect(Collectors.toSet());
        return items.stream().collect(Collectors.toMap(
                QuestionItem::sourceId,
                item -> QuestionSourceSummary.from(item, subscribedQuestionIds.contains(item.id())),
                (left, right) -> left
        ));
    }

    public QuestionResponse findById(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        return findResponse(questionId, currentUserId);
    }

    @Transactional
    public QuestionSubscriptionResponse subscribe(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        if (currentUserId.equals(question.askerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "问题发起者会自动接收结果，无需订阅");
        }
        if (!questionMapper.hasSubscription(questionId, currentUserId)) {
            try {
                questionMapper.insertSubscription(questionId, currentUserId);
                questionMapper.increaseSubscriberCount(questionId);
            } catch (DuplicateKeyException ignored) {
                // Concurrent subscribe requests converge to a single subscription.
            }
        }
        return new QuestionSubscriptionResponse(true, requireQuestion(questionId).subscriberCount());
    }

    @Transactional
    public QuestionSubscriptionResponse unsubscribe(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        requireQuestion(questionId);
        if (questionMapper.deleteSubscription(questionId, currentUserId) > 0) {
            questionMapper.decreaseSubscriberCount(questionId);
        }
        return new QuestionSubscriptionResponse(false, requireQuestion(questionId).subscriberCount());
    }

    public PageResponse<QuestionResponse> listMyQuestions(String authorization, MyQuestionRole role, int page, int size) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        long total = role == MyQuestionRole.ASKED
                ? questionMapper.countAskedByUserId(currentUserId)
                : questionMapper.countSubscribedByUserId(currentUserId);
        List<QuestionItem> records = role == MyQuestionRole.ASKED
                ? questionMapper.findAskedByUserId(currentUserId, size, (page - 1) * size)
                : questionMapper.findSubscribedByUserId(currentUserId, size, (page - 1) * size);
        return PageResponse.of(page, size, total, records.stream()
                .map(item -> toResponse(item, currentUserId, false))
                .toList());
    }

    @Transactional
    public void deleteQuestion(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureCanManageSource(currentUserId, question.askerId());
        List<Long> subscriberIds = questionMapper.findSubscriberIds(questionId);
        questionAnswerMapper.deleteByQuestionId(questionId);
        questionMapper.deleteSubscriptionsByQuestionId(questionId);
        questionMapper.deleteById(questionId);
        domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.deleted(
                questionId, currentUserId, question.sourcePostId(),
                QuestionSourceType.valueOf(question.sourceType()) == QuestionSourceType.COMMENT ? question.sourceId() : null,
                subscriberIds
        ));
    }

    private QuestionResponse findResponse(Long questionId, Long currentUserId) {
        QuestionItem question = requireQuestion(questionId);
        return toResponse(question, currentUserId, true);
    }

    private QuestionResponse toResponse(QuestionItem question, Long currentUserId) {
        return toResponse(question, currentUserId, true);
    }

    private QuestionResponse toResponse(QuestionItem question, Long currentUserId, boolean includeApprovedAnswers) {
        List<QuestionAnswerResponse> approvedAnswers = includeApprovedAnswers
                ? questionAnswerMapper.selectAcceptedByQuestionId(question.id()).stream().map(QuestionAnswerResponse::from).toList()
                : List.of();
        return QuestionResponse.from(question, questionMapper.hasSubscription(question.id(), currentUserId), approvedAnswers);
    }

    private QuestionItem requireQuestion(Long questionId) {
        QuestionItem question = questionMapper.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题追踪不存在或原内容已删除"));
        resolveNormalSource(QuestionSourceType.valueOf(question.sourceType()), question.sourceId());
        return question;
    }

    private SourceNode resolveNormalSource(QuestionSourceType sourceType, Long sourceId) {
        if (sourceType == QuestionSourceType.POST) {
            PostDetail post = postMapper.findDetailById(sourceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
            if (post.status() != 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
            }
            return new SourceNode(post.userId());
        }
        CommentDetail comment = commentMapper.findDetailById(sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论不存在"));
        if (comment.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        if (comment.rootCommentId() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅一级评论可以发起问题追踪");
        }
        PostDetail post = postMapper.findDetailById(comment.postId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
        if (post.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
        return new SourceNode(comment.userId());
    }

    private void ensureCanManageSource(Long currentUserId, Long sourceAuthorId) {
        if (currentUserId.equals(sourceAuthorId)) {
            return;
        }
        UserProfile currentUser = userMapper.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (currentUser.role() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为自己发布的内容发起或管理问题追踪");
        }
    }

    private record SourceNode(Long authorId) {
    }
}
