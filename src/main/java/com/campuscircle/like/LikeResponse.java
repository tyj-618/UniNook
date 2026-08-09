package com.campuscircle.like;

public record LikeResponse(
        boolean liked,
        int likeCount
) {
}
