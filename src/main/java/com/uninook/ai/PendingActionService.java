package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.uninook.post.CreatePostRequest;
import com.uninook.post.CreatePostResponse;
import com.uninook.post.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PendingActionService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionService.class);

    // Must stay aligned with CreatePostRequest validation limits and the post table columns.
    private static final int MAX_POST_TITLE_LENGTH = 100;
    private static final int MAX_POST_CONTENT_LENGTH = 5000;

    private final PendingActionStore pendingActionStore;
    private final AiProperties properties;
    private final CurrentUserService currentUserService;
    private final PostService postService;

    public PendingActionService(PendingActionStore pendingActionStore, AiProperties properties,
                                CurrentUserService currentUserService, PostService postService) {
        this.pendingActionStore = pendingActionStore;
        this.properties = properties;
        this.currentUserService = currentUserService;
        this.postService = postService;
    }

    public PendingActionSummary preparePostDraft(ToolExecutionContext context, Map<String, Object> arguments) {
        String title = String.valueOf(arguments.get("title")).trim();
        String content = String.valueOf(arguments.get("content")).trim();
        validatePostText(title, content);
        Instant expiresAt = Instant.now().plusSeconds(properties.getPendingActionTtlSeconds());
        PendingAction action = new PendingAction(UUID.randomUUID().toString(), PendingActionType.CREATE_POST,
                context.userId(), title, content, expiresAt);
        pendingActionStore.save(action);
        log.info("assistant requestId={} stage=pending-action status=prepared actionId={} userId={} expiresAt={}",
                AiRequestContext.requestId(), action.actionId(), context.userId(), expiresAt);
        return action.summary();
    }

    public java.util.Optional<PendingAction> loadForUser(Long userId, String actionId) {
        return pendingActionStore.load(userId, actionId);
    }

    public ConfirmPendingActionResponse confirmPost(String authorization, String actionId, Long categoryId) {
        Long userId = currentUserService.requireUserId(authorization);
        PendingAction action = pendingActionStore.take(userId, actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "待确认草稿不存在或已过期"));
        if (action.type() != PendingActionType.CREATE_POST) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该待确认动作不支持发布帖子");
        }
        CreatePostResponse response = postService.createPost(authorization,
                new CreatePostRequest(categoryId, action.title(), action.content()));
        log.info("assistant requestId={} stage=pending-action status=confirmed actionId={} userId={} postId={} categoryId={}",
                AiRequestContext.requestId(), action.actionId(), userId, response.postId(), categoryId);
        return new ConfirmPendingActionResponse(action.actionId(), response.postId());
    }

    public void cancel(String authorization, String actionId) {
        Long userId = currentUserService.requireUserId(authorization);
        PendingAction action = pendingActionStore.load(userId, actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "待确认草稿不存在或已过期"));
        pendingActionStore.delete(action.userId(), action.actionId());
        log.info("assistant requestId={} stage=pending-action status=cancelled actionId={} userId={}",
                AiRequestContext.requestId(), action.actionId(), userId);
    }

    private void validatePostText(String title, String content) {
        if (title.isBlank() || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "帖子标题和正文不能为空");
        }
        if (title.length() > MAX_POST_TITLE_LENGTH || content.length() > MAX_POST_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "帖子内容超出长度限制");
        }
    }
}
