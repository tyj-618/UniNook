package com.campuscircle.user;

import java.time.LocalDateTime;

public record UserProfileResponse(
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
        Integer role,
        Integer status,
        boolean nicknameSetupRequired,
        LocalDateTime createdAt
) {

    public static UserProfileResponse from(UserProfile userProfile) {
        return new UserProfileResponse(
                userProfile.id(),
                userProfile.username(),
                userProfile.nickname(),
                userProfile.schoolId(),
                userProfile.universityId(),
                userProfile.schoolName(),
                userProfile.campusName(),
                userProfile.schoolCity(),
                userProfile.avatarUrl(),
                userProfile.bio(),
                userProfile.role(),
                userProfile.status(),
                !Boolean.TRUE.equals(userProfile.nicknameConfirmed()),
                userProfile.createdAt()
        );
    }
}
