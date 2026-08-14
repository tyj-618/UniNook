package com.uninook.admin;

import com.uninook.common.ApiResponse;
import com.uninook.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PutMapping("/posts/{postId}/hide")
    public ApiResponse<Boolean> hidePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        adminService.hidePost(postId, authorization);
        return ApiResponse.success(true);
    }

    @PutMapping("/posts/{postId}/restore")
    public ApiResponse<Boolean> restorePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        adminService.restorePost(postId, authorization);
        return ApiResponse.success(true);
    }

    @PutMapping("/users/{userId}/disable")
    public ApiResponse<Boolean> disableUser(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        adminService.disableUser(userId, authorization);
        return ApiResponse.success(true);
    }

    @PutMapping("/users/{userId}/enable")
    public ApiResponse<Boolean> enableUser(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        adminService.enableUser(userId, authorization);
        return ApiResponse.success(true);
    }

    @PostMapping("/search/posts/reindex")
    public ApiResponse<Integer> rebuildPostSearchIndex(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(adminService.rebuildPostSearchIndex(authorization));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<AdminPostListItem>> listPosts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(2) Integer status) {
        return ApiResponse.success(adminService.listPosts(authorization, page, size, keyword, status));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserListItem>> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return ApiResponse.success(adminService.listUsers(authorization, page, size, keyword, status));
    }

    @GetMapping("/action-logs")
    public ApiResponse<PageResponse<AdminActionLogItem>> listActionLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(adminService.listActionLogs(authorization, page, size));
    }

    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminReportListItem>> listReports(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.listReports(authorization, page, size, status));
    }

    @PostMapping("/reports/{reportId}/process")
    public ApiResponse<Boolean> processReport(
            @PathVariable Long reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @jakarta.validation.Valid @RequestBody ProcessReportRequest request) {
        adminService.processReport(reportId, request, authorization);
        return ApiResponse.success(true);
    }

    @GetMapping("/feedback-stats")
    public ApiResponse<AdminFeedbackStatsResponse> feedbackStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(adminService.feedbackStats(authorization));
    }
}
