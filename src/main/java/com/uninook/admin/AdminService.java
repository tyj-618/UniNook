package com.uninook.admin;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.AfterCommitExecutor;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.uninook.user.UserProfile;
import com.uninook.user.UserMapper;
import com.uninook.event.DomainEventPublisher;
import com.uninook.event.PostSearchIndexEvent;
import com.uninook.ai.PostSearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final int USER_STATUS_NORMAL = 0;
    private static final int USER_STATUS_DISABLED = 1;
    private static final int POST_STATUS_NORMAL = 0;
    private static final int POST_STATUS_HIDDEN = 2;

    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final DomainEventPublisher domainEventPublisher;
    private final PostSearchIndexService postSearchIndexService;
    private final AfterCommitExecutor afterCommitExecutor;

    public AdminService(CurrentUserService currentUserService, UserMapper userMapper, AdminMapper adminMapper,
                        DomainEventPublisher domainEventPublisher, PostSearchIndexService postSearchIndexService,
                        AfterCommitExecutor afterCommitExecutor) {
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
        this.domainEventPublisher = domainEventPublisher;
        this.postSearchIndexService = postSearchIndexService;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Transactional
    public void hidePost(Long postId, String authorization) {
        requireAdmin(authorization);
        ensurePostExists(postId);
        adminMapper.updatePostStatus(postId, POST_STATUS_HIDDEN);
        publishIndexEventAfterCommit(postId);
    }

    @Transactional
    public void restorePost(Long postId, String authorization) {
        requireAdmin(authorization);
        ensurePostExists(postId);
        adminMapper.updatePostStatus(postId, POST_STATUS_NORMAL);
        publishIndexEventAfterCommit(postId);
    }

    public void disableUser(Long userId, String authorization) {
        Long currentUserId = requireAdmin(authorization);
        if (currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能禁用当前登录的管理员账号");
        }

        ensureUserExists(userId);
        adminMapper.updateUserStatus(userId, USER_STATUS_DISABLED);
    }

    public void enableUser(Long userId, String authorization) {
        requireAdmin(authorization);
        ensureUserExists(userId);
        adminMapper.updateUserStatus(userId, USER_STATUS_NORMAL);
    }

    public int rebuildPostSearchIndex(String authorization) {
        requireAdmin(authorization);
        return postSearchIndexService.rebuildAll();
    }

    private void publishIndexEventAfterCommit(Long postId) {
        afterCommitExecutor.execute(() -> domainEventPublisher.publishPostSearchIndex(PostSearchIndexEvent.forPost(postId)));
    }

    private Long requireAdmin(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = userMapper.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (currentUser.status() != USER_STATUS_NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用");
        }

        if (currentUser.role() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }

        return currentUserId;
    }

    private void ensurePostExists(Long postId) {
        if (!adminMapper.existsPost(postId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
    }

    private void ensureUserExists(Long userId) {
        if (!adminMapper.existsUser(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }
}
