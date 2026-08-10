package com.uninook.like;

public record LikeResponse(
        boolean liked,
        int likeCount
) {
}
