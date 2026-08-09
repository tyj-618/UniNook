package com.campuscircle.comment;

import com.campuscircle.common.ApiResponse;
import com.campuscircle.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;
    private final CommentLikeService commentLikeService;

    public CommentController(CommentService commentService, CommentLikeService commentLikeService) {
        this.commentService = commentService;
        this.commentLikeService = commentLikeService;
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CreateCommentResponse> createComment(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-CampusCircle-User-Id", required = false) Long clientUserId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(commentService.createComment(postId, authorization, clientUserId, radiusKm, request));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> listPostComments(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) Long focusCommentId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(commentService.listPostComments(postId, authorization, page, size, focusCommentId, radiusKm));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Boolean> deleteComment(
            @PathVariable Long commentId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        commentService.deleteComment(commentId, authorization);
        return ApiResponse.success(true);
    }

    @PostMapping("/comments/{commentId}/like")
    public ApiResponse<CommentLikeResponse> likeComment(
            @PathVariable Long commentId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(commentLikeService.likeComment(commentId, authorization, radiusKm));
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<CommentLikeResponse> unlikeComment(
            @PathVariable Long commentId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(commentLikeService.unlikeComment(commentId, authorization, radiusKm));
    }

    @GetMapping("/users/me/comments")
    public ApiResponse<PageResponse<MyCommentResponse>> listMyComments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(commentService.listMyComments(authorization, page, size));
    }
}
