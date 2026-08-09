package com.campuscircle.user;

import com.campuscircle.common.ApiResponse;
import com.campuscircle.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserActivityService userActivityService;

    public UserController(UserService userService, UserActivityService userActivityService) {
        this.userService = userService;
        this.userActivityService = userActivityService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(userService.getCurrentUser(authorization));
    }

    @GetMapping("/me/school-change-quota")
    public ApiResponse<SchoolChangeQuotaResponse> getSchoolChangeQuota(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(userService.getSchoolChangeQuota(authorization));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(userService.updateCurrentUser(authorization, request));
    }

    @PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarUploadResponse> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(userService.uploadAvatar(authorization, file));
    }

    @GetMapping("/me/likes")
    public ApiResponse<PageResponse<MyLikeResponse>> listMyLikes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(userActivityService.listMyLikes(authorization, page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicUserProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ApiResponse.success(userService.getPublicUserProfile(userId));
    }
}
