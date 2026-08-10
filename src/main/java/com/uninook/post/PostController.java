package com.uninook.post;

import com.uninook.common.ApiResponse;
import com.uninook.common.CursorPageResponse;
import com.uninook.common.PageResponse;
import com.uninook.school.CampusScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/posts")
    public ApiResponse<CreatePostResponse> createPost(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.createPost(authorization, request));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> listPosts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) CampusScope scope,
            @RequestParam(required = false) @Min(1) @Max(50) Double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|hot") String sort) {
        return ApiResponse.success(postService.listPosts(
                authorization, page, size, CampusScope.resolve(scope, radiusKm), categoryId, keyword, sort));
    }

    @GetMapping("/posts/feed")
    public ApiResponse<PageResponse<PostListItemResponse>> listNearbyFeed(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) CampusScope scope,
            @RequestParam(required = false) @Min(1) @Max(50) Double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|hot") String sort) {
        return ApiResponse.success(postService.listNearbyFeed(
                authorization, page, size, CampusScope.resolve(scope, radiusKm), categoryId, sort));
    }

    @GetMapping("/posts/feed/cursor")
    public ApiResponse<CursorPageResponse<PostListItemResponse>> listNearbyFeedByCursor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) CampusScope scope,
            @RequestParam(required = false) @Min(1) @Max(50) Double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success(postService.listNearbyFeedByCursor(
                authorization, size, CampusScope.resolve(scope, radiusKm), categoryId, cursor));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(postService.getPostDetail(postId, authorization, radiusKm));
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<Boolean> updatePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdatePostRequest request) {
        postService.updatePost(postId, authorization, request);
        return ApiResponse.success(true);
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Boolean> deletePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        postService.deletePost(postId, authorization);
        return ApiResponse.success(true);
    }

    @GetMapping("/users/{userId}/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> listUserPosts(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(postService.listUserPosts(authorization, userId, page, size, radiusKm));
    }

    @GetMapping("/posts/hot")
    public ApiResponse<List<PostHotItemResponse>> listHotPosts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) CampusScope scope,
            @RequestParam(required = false) @Min(1) @Max(50) Double radiusKm,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(postService.listHotPosts(
                authorization, limit, CampusScope.resolve(scope, radiusKm), categoryId));
    }
}
