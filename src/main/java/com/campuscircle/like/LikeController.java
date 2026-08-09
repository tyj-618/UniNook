package com.campuscircle.like;

import com.campuscircle.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/like")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public ApiResponse<LikeResponse> likePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(likeService.likePost(postId, authorization, radiusKm));
    }

    @DeleteMapping
    public ApiResponse<LikeResponse> unlikePost(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(likeService.unlikePost(postId, authorization, radiusKm));
    }

    @GetMapping
    public ApiResponse<LikeStatusResponse> getLikeStatus(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(likeService.getLikeStatus(postId, authorization, radiusKm));
    }
}
