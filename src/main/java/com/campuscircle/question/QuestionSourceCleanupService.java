package com.campuscircle.question;

import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.event.QuestionLifecycleEvent;
import org.springframework.stereotype.Service;

@Service
public class QuestionSourceCleanupService {

    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final DomainEventPublisher domainEventPublisher;

    public QuestionSourceCleanupService(QuestionMapper questionMapper, QuestionAnswerMapper questionAnswerMapper,
                                        DomainEventPublisher domainEventPublisher) {
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    public void deleteByPostId(Long postId, Long actorId) {
        for (QuestionSourceCleanupItem question : questionMapper.findByPostId(postId)) {
            var subscriberIds = questionMapper.findSubscriberIds(question.questionId());
            questionAnswerMapper.deleteByQuestionId(question.questionId());
            questionMapper.deleteSubscriptionsByQuestionId(question.questionId());
            questionMapper.deleteById(question.questionId());
            domainEventPublisher.publishQuestionLifecycle(QuestionLifecycleEvent.deleted(
                    question.questionId(), actorId, postId,
                    QuestionSourceType.COMMENT.name().equals(question.sourceType()) ? question.sourceCommentId() : null,
                    subscriberIds
            ));
        }
    }
}
