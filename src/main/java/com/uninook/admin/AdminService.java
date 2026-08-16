package com.uninook.admin;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.AfterCommitExecutor;
import com.uninook.common.ErrorCode;
import com.uninook.common.PageResponse;
import com.uninook.exception.BusinessException;
import com.uninook.user.UserProfile;
import com.uninook.user.UserMapper;
import com.uninook.event.DomainEventPublisher;
import com.uninook.event.PostSearchIndexEvent;
import com.uninook.ai.PostSearchIndexService;
import com.uninook.report.ReportStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final int USER_STATUS_NORMAL = 0;
    private static final int USER_STATUS_DISABLED = 1;
    private static final int POST_STATUS_NORMAL = 0;
    private static final int POST_STATUS_HIDDEN = 2;
    private static final int FEEDBACK_STATS_LIMIT = 20;

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
        Long adminUserId = requireAdmin(authorization);
        ensurePostExists(postId);
        adminMapper.updatePostStatus(postId, POST_STATUS_HIDDEN);
        adminMapper.insertActionLog(adminUserId, "POST", postId, "HIDE_POST");
        publishIndexEventAfterCommit(postId);
    }

    @Transactional
    public void restorePost(Long postId, String authorization) {
        Long adminUserId = requireAdmin(authorization);
        ensurePostExists(postId);
        adminMapper.updatePostStatus(postId, POST_STATUS_NORMAL);
        adminMapper.insertActionLog(adminUserId, "POST", postId, "RESTORE_POST");
        publishIndexEventAfterCommit(postId);
    }

    @Transactional
    public void disableUser(Long userId, String authorization) {
        Long currentUserId = requireAdmin(authorization);
        if (currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能禁用当前登录的管理员账号");
        }

        ensureUserExists(userId);
        adminMapper.updateUserStatus(userId, USER_STATUS_DISABLED);
        adminMapper.insertActionLog(currentUserId, "USER", userId, "DISABLE_USER");
    }

    @Transactional
    public void enableUser(Long userId, String authorization) {
        Long adminUserId = requireAdmin(authorization);
        ensureUserExists(userId);
        adminMapper.updateUserStatus(userId, USER_STATUS_NORMAL);
        adminMapper.insertActionLog(adminUserId, "USER", userId, "ENABLE_USER");
    }

    @Transactional
    public int rebuildPostSearchIndex(String authorization) {
        Long adminUserId = requireAdmin(authorization);
        int rebuilt = postSearchIndexService.rebuildAll();
        adminMapper.insertActionLog(adminUserId, "SEARCH", null, "REBUILD_POST_INDEX");
        return rebuilt;
    }

    public PageResponse<AdminPostListItem> listPosts(String authorization, int page, int size, String keyword,
                                                      Integer status) {
        requireAdmin(authorization);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = adminMapper.countPosts(normalizedKeyword, status);
        return PageResponse.of(page, size, total,
                adminMapper.selectPosts(normalizedKeyword, status, size, (page - 1) * size));
    }

    public PageResponse<AdminUserListItem> listUsers(String authorization, int page, int size, String keyword,
                                                      Integer status) {
        requireAdmin(authorization);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = adminMapper.countUsers(normalizedKeyword, status);
        return PageResponse.of(page, size, total,
                adminMapper.selectUsers(normalizedKeyword, status, size, (page - 1) * size));
    }

    public PageResponse<AdminActionLogItem> listActionLogs(String authorization, int page, int size) {
        requireAdmin(authorization);
        long total = adminMapper.countActionLogs();
        return PageResponse.of(page, size, total, adminMapper.selectActionLogs(size, (page - 1) * size));
    }

    public PageResponse<AdminReportListItem> listReports(String authorization, int page, int size, String status) {
        requireAdmin(authorization);
        String normalizedStatus = normalizeReportStatus(status, true);
        long total = adminMapper.countReports(normalizedStatus);
        return PageResponse.of(page, size, total,
                adminMapper.selectReports(normalizedStatus, size, (page - 1) * size));
    }

    @Transactional
    public void processReport(Long reportId, ProcessReportRequest request, String authorization) {
        Long adminUserId = requireAdmin(authorization);
        if (adminMapper.countReportById(reportId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }
        String status = normalizeReportStatus(request.status(), false);
        int updated = adminMapper.processReport(reportId, status, adminUserId, trimToNull(request.adminNote()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该举报已处理，不能重复处理");
        }
        adminMapper.insertActionLog(adminUserId, "REPORT", reportId, "PROCESS_" + status);
    }

    public AdminFeedbackStatsResponse feedbackStats(String authorization) {
        requireAdmin(authorization);
        var lowQualityAnswers = adminMapper.selectFeedbackRatingSummaries(FEEDBACK_STATS_LIMIT).stream()
                .map(summary -> new LowQualityAnswerItem(summary.requestId(), summary.helpfulCount(),
                        summary.unhelpfulCount(), summary.unhelpfulRate()))
                .sorted((left, right) -> Double.compare(right.unhelpfulRate(), left.unhelpfulRate()))
                .toList();
        return new AdminFeedbackStatsResponse(lowQualityAnswers, adminMapper.selectFrequentQuestions(FEEDBACK_STATS_LIMIT));
    }

    private void publishIndexEventAfterCommit(Long postId) {
        afterCommitExecutor.execute(() -> domainEventPublisher.publishPostSearchIndex(PostSearchIndexEvent.forPost(postId)));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim();
    }

    private String normalizeReportStatus(String status, boolean allowEmpty) {
        if (status == null || status.isBlank()) {
            return allowEmpty ? null : invalidReportStatus();
        }
        try {
            ReportStatus parsed = ReportStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
            if (!allowEmpty && parsed == ReportStatus.PENDING) {
                return invalidReportStatus();
            }
            return parsed.name();
        } catch (IllegalArgumentException exception) {
            return invalidReportStatus();
        }
    }

    private String invalidReportStatus() {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "举报状态仅支持 PENDING、PROCESSED 或 REJECTED；处理时仅支持后两项");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
