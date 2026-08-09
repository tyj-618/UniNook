package com.campuscircle.user;

import java.time.LocalDateTime;

public record PublicUserProfileResponse(
        Long id,
        String username,
        String nickname,
        Long schoolId,
        Long universityId,
        String schoolName,
        String campusName,
        String schoolCity,
        String avatarUrl,
        String bio,
        long postCount,
        long commentCount,
        long likeCount,
        LocalDateTime createdAt
) {
}
