package com.uninook.notice;

import com.uninook.auth.CurrentUserService;
import com.uninook.comment.CommentMapper;
import com.uninook.common.ErrorCode;
import com.uninook.common.PageResponse;
import com.uninook.exception.BusinessException;
import com.uninook.event.QuestionLifecycleEvent;
import com.uninook.event.QuestionLifecycleEventType;
import com.uninook.post.PageQueryResult;
import com.uninook.post.PostMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeService {

    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_LIKE = 2;
    public static final int TYPE_COMMENT_LIKE = 3;
    public static final int TYPE_QUESTION_ANSWER = 5;
    public static final int TYPE_QUESTION_COMPLETED = 6;
    public static final int TYPE_QUESTION_DELETED = 7;
    public static final int TYPE_QUESTION_REOPENED = 8;
    public static final int TYPE_QUESTION_ANSWER_ACCEPTED = 9;

    private final CurrentUserService currentUserService;
    private final NoticeMapper noticeMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    public NoticeService(CurrentUserService currentUserService, NoticeMapper noticeMapper,
                         PostMapper postMapper, CommentMapper commentMapper) {
        this.currentUserService = currentUserService;
        this.noticeMapper = noticeMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
    }

    public void createCommentNotice(String eventId, Long receiverId, Long senderId, Long postId, Long commentId) {
        if (receiverId.equals(senderId)) {
            return;
        }
        noticeMapper.saveNotice(new CreateNoticeCommand(
                receiverId,
                senderId,
                postId,
                commentId,
                null,
                TYPE_COMMENT,
                buildEventKey(eventId),
                "评论了《" + postTitle(postId) + "》：“" + commentPreview(commentId) + "”"
        ));
    }

    public void createLikeNotice(String eventId, Long receiverId, Long senderId, Long postId) {
        if (receiverId.equals(senderId)) {
            return;
        }
        noticeMapper.saveNotice(new CreateNoticeCommand(
                receiverId,
                senderId,
                postId,
                null,
                null,
                TYPE_LIKE,
                buildEventKey(eventId),
                "赞了你的帖子《" + postTitle(postId) + "》"
        ));
    }

    public void createCommentLikeNotice(String eventId, Long receiverId, Long senderId, Long postId, Long commentId) {
        if (receiverId.equals(senderId)) {
            return;
        }
        noticeMapper.saveNotice(new CreateNoticeCommand(
                receiverId,
                senderId,
                postId,
                commentId,
                null,
                TYPE_COMMENT_LIKE,
                buildEventKey(eventId),
                "赞了你在《" + postTitle(postId) + "》中的评论：“" + commentPreview(commentId) + "”"
        ));
    }

    public void createQuestionLifecycleNotices(QuestionLifecycleEvent event) {
        for (Long receiverId : event.receiverIds()) {
            if (receiverId.equals(event.senderId())) {
                continue;
            }
            NoticeTemplate template = questionNoticeTemplate(event.type(), event.answerPreview());
            noticeMapper.saveNotice(new CreateNoticeCommand(
                    receiverId, event.senderId(), event.postId(), event.commentId(), event.questionId(),
                    template.type(), buildEventKey(event.eventId(), receiverId), template.content()
            ));
        }
    }

    private NoticeTemplate questionNoticeTemplate(QuestionLifecycleEventType type, String answerPreview) {
        return switch (type) {
            case CANDIDATE_SUBMITTED -> new NoticeTemplate(TYPE_QUESTION_ANSWER, "有人提交了候选答复：" + abbreviate(answerPreview, 48));
            case ANSWER_ACCEPTED -> new NoticeTemplate(TYPE_QUESTION_ANSWER_ACCEPTED, "你订阅的问题有答复被通过：" + abbreviate(answerPreview, 48));
            case COMPLETED -> new NoticeTemplate(TYPE_QUESTION_COMPLETED, "你订阅的问题已结束：" + abbreviate(answerPreview, 48));
            case REOPENED -> new NoticeTemplate(TYPE_QUESTION_REOPENED, "你订阅的问题已重新开启，正在继续收集答复");
            case DELETED -> new NoticeTemplate(TYPE_QUESTION_DELETED, "问题发起者已删除该问题追踪");
        };
    }

    public PageResponse<NoticeResponse> listNotices(String authorization, int page, int size, Integer readStatus) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        validateReadStatus(readStatus);
        PageQueryResult<NoticeItem> result = noticeMapper.findNoticesByReceiverId(currentUserId, page, size, readStatus);
        List<NoticeResponse> records = result.records().stream().map(NoticeResponse::from).toList();
        return PageResponse.of(page, size, result.total(), records);
    }

    public UnreadNoticeCountResponse countUnreadNotices(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        return new UnreadNoticeCountResponse(noticeMapper.countUnreadByReceiverId(currentUserId));
    }

    public void markNoticeRead(Long noticeId, String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        int updatedCount = noticeMapper.markRead(noticeId, currentUserId);
        if (updatedCount == 0 && !noticeMapper.existsByIdAndReceiverId(noticeId, currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
    }

    public UpdateNoticeCountResponse markAllNoticesRead(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        int updatedCount = noticeMapper.markAllRead(currentUserId);
        return new UpdateNoticeCountResponse(updatedCount);
    }

    public UpdateNoticeCountResponse clearReadNotices(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        int deletedCount = noticeMapper.deleteReadByReceiverId(currentUserId);
        return new UpdateNoticeCountResponse(deletedCount);
    }

    private void validateReadStatus(Integer readStatus) {
        if (readStatus != null && readStatus != 0 && readStatus != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通知阅读状态只能是 0 或 1");
        }
    }

    private String buildEventKey(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "事件标识不能为空");
        }
        return "event:" + eventId;
    }

    private String buildEventKey(String eventId, Long receiverId) {
        return buildEventKey(eventId) + ":receiver:" + receiverId;
    }

    private String postTitle(Long postId) {
        return postMapper.findDetailById(postId)
                .map(post -> abbreviate(post.title(), 36))
                .orElse("已删除的帖子");
    }

    private String commentPreview(Long commentId) {
        return commentMapper.findDetailById(commentId)
                .map(comment -> abbreviate(comment.content(), 48))
                .orElse("已删除的评论");
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "无内容";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private record NoticeTemplate(int type, String content) {
    }
}
