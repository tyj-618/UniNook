package com.campuscircle.question;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.comment.CommentDetail;
import com.campuscircle.comment.CommentMapper;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.common.entity.QuestionAnswerEntity;
import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.event.QuestionLifecycleEvent;
import com.campuscircle.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionAnswerService {

    private final CurrentUserService currentUserService;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final CommentMapper commentMapper;
    private final DomainEventPublisher domainEventPublisher;
    private final CandidateAnswerAiReviewer candidateAnswerAiReviewer;

    public QuestionAnswerService(CurrentUserService currentUserService, QuestionMapper questionMapper,
                                 QuestionAnswerMapper questionAnswerMapper, CommentMapper commentMapper,
                                 DomainEventPublisher domainEventPublisher,
                                 CandidateAnswerAiReviewer candidateAnswerAiReviewer) {
        this.currentUserService = currentUserService;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.commentMapper = commentMapper;
        this.domainEventPublisher = domainEventPublisher;
        this.candidateAnswerAiReviewer = candidateAnswerAiReviewer;
    }

    @Transactional
    public void registerCandidate(Long questionId, Long commentId, Long answererId, Long postId, Long parentCommentId) {
        QuestionItem question = requireQuestion(questionId);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已经完成，不能继续提交候选答复");
        }
        validateCandidateSource(question, postId, parentCommentId);

        QuestionAnswerEntity answer = new QuestionAnswerEntity();
        answer.setQuestionId(questionId);
        answer.setCommentId(commentId);
        answer.setAnswererId(answererId);
        answer.setStatus(QuestionAnswerStatus.PENDING.name());
        try {
            questionAnswerMapper.insert(answer);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "该评论已经作为候选答复提交");
        }
        questionMapper.touchAnswerTime(questionId);
        CommentDetail comment = commentMapper.findDetailById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选答复评论不存在"));
        domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.candidateSubmitted(
                questionId, question.askerId(), answererId, postId, commentId, comment.content()
        ));
    }

    public List<QuestionAnswerResponse> listAnswers(String authorization, Long questionId) {
        currentUserService.requireUserId(authorization);
        requireQuestion(questionId);
        return questionAnswerMapper.selectByQuestionId(questionId).stream().map(QuestionAnswerResponse::from).toList();
    }

    public CandidateAnswerAiReviewResponse reviewWithAi(String authorization, Long questionId, Long answerId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureAsker(currentUserId, question);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.CONFLICT, "已完成的问题无需继续评估候选答复");
        }
        QuestionAnswerItem answer = questionAnswerMapper.findByIdAndQuestionId(answerId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选答复不存在"));
        if (QuestionAnswerStatus.valueOf(answer.status()) != QuestionAnswerStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待判断的候选答复可以请求 AI 辅助判断");
        }
        return candidateAnswerAiReviewer.review(currentUserId, question, answer);
    }

    @Transactional
    public QuestionResponse accept(String authorization, Long questionId, Long answerId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureAsker(currentUserId, question);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已经完成");
        }
        QuestionAnswerItem answer = questionAnswerMapper.findByIdAndQuestionId(answerId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选答复不存在"));
        if (QuestionAnswerStatus.valueOf(answer.status()) != QuestionAnswerStatus.PENDING
                || questionAnswerMapper.accept(questionId, answerId, currentUserId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选答复状态已变化，请刷新后重试");
        }
        domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.answerAccepted(
                questionId, currentUserId, answer.postId(), answer.commentId(), answer.content(),
                questionMapper.findSubscriberIds(questionId)
        ));
        return findResponse(questionId, currentUserId);
    }

    @Transactional
    public QuestionResponse complete(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureAsker(currentUserId, question);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已经结束");
        }
        List<QuestionAnswerItem> approvedAnswers = questionAnswerMapper.selectAcceptedByQuestionId(questionId);
        if (approvedAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "至少通过一条候选答复后才能结束问题");
        }
        if (questionMapper.complete(questionId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
        }
        QuestionAnswerItem firstApprovedAnswer = approvedAnswers.get(0);
        domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.completed(
                questionId, currentUserId, firstApprovedAnswer.postId(), firstApprovedAnswer.commentId(),
                "已确认 " + approvedAnswers.size() + " 条有效答复",
                questionMapper.findSubscriberIds(questionId)
        ));
        return findResponse(questionId, currentUserId);
    }

    @Transactional
    public QuestionResponse reopen(String authorization, Long questionId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureAsker(currentUserId, question);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已完成的问题可以重新开启");
        }
        if (questionMapper.reopen(questionId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题状态已变化，请刷新后重试");
        }
        domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.reopened(
                questionId, currentUserId, question.sourcePostId(),
                QuestionSourceType.valueOf(question.sourceType()) == QuestionSourceType.COMMENT ? question.sourceId() : null,
                questionMapper.findSubscriberIds(questionId)
        ));
        return findResponse(questionId, currentUserId);
    }

    @Transactional
    public QuestionAnswerResponse reject(String authorization, Long questionId, Long answerId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        QuestionItem question = requireQuestion(questionId);
        ensureAsker(currentUserId, question);
        if (QuestionStatus.valueOf(question.status()) != QuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题已完成，不能再修改候选答复状态");
        }
        if (questionAnswerMapper.reject(questionId, answerId, currentUserId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待定候选答复可以标记为无效");
        }
        return questionAnswerMapper.findByIdAndQuestionId(answerId, questionId)
                .map(QuestionAnswerResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选答复不存在"));
    }

    public void withdrawCommentCandidate(Long commentId) {
        List<Long> acceptedQuestionIds = questionAnswerMapper.selectAcceptedQuestionIdsByCommentId(commentId);
        questionAnswerMapper.withdrawByCommentId(commentId);
        reopenQuestionsWithoutApprovedAnswers(acceptedQuestionIds);
    }

    public void withdrawCommentThreadCandidates(Long rootCommentId) {
        List<Long> acceptedQuestionIds = questionAnswerMapper.selectAcceptedQuestionIdsByRootCommentId(rootCommentId);
        questionAnswerMapper.withdrawByRootCommentId(rootCommentId);
        reopenQuestionsWithoutApprovedAnswers(acceptedQuestionIds);
    }

    QuestionResponse findResponse(Long questionId, Long currentUserId) {
        QuestionItem question = requireQuestion(questionId);
        List<QuestionAnswerResponse> approvedAnswers = questionAnswerMapper.selectAcceptedByQuestionId(questionId).stream()
                .map(QuestionAnswerResponse::from)
                .toList();
        return QuestionResponse.from(question, questionMapper.hasSubscription(questionId, currentUserId), approvedAnswers);
    }

    private void validateCandidateSource(QuestionItem question, Long postId, Long parentCommentId) {
        if (QuestionSourceType.valueOf(question.sourceType()) == QuestionSourceType.POST && question.sourceId().equals(postId)) return;
        if (QuestionSourceType.valueOf(question.sourceType()) == QuestionSourceType.COMMENT && question.sourceId().equals(parentCommentId)) return;
        throw new BusinessException(ErrorCode.PARAM_ERROR, "候选答复必须直接回应问题来源内容");
    }

    private QuestionItem requireQuestion(Long questionId) {
        return questionMapper.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题追踪不存在或原内容已删除"));
    }

    private void ensureAsker(Long currentUserId, QuestionItem question) {
        if (!currentUserId.equals(question.askerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有问题发起者可以确认候选答复、结束或重新开启问题");
        }
    }

    private void reopenQuestionsWithoutApprovedAnswers(List<Long> questionIds) {
        for (Long questionId : questionIds) {
            questionMapper.findById(questionId).ifPresent(question -> {
                boolean hasNoApprovedAnswer = questionAnswerMapper.selectAcceptedByQuestionId(questionId).isEmpty();
                if (QuestionStatus.valueOf(question.status()) == QuestionStatus.COMPLETED
                        && hasNoApprovedAnswer
                        && questionMapper.reopen(questionId) > 0) {
                    domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.reopened(
                            questionId,
                            question.askerId(),
                            question.sourcePostId(),
                            QuestionSourceType.valueOf(question.sourceType()) == QuestionSourceType.COMMENT
                                    ? question.sourceId() : null,
                            questionMapper.findSubscriberIds(questionId)
                    ));
                }
            });
        }
    }
}
