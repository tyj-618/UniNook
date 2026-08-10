package com.uninook.user;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;

import java.time.LocalDateTime;

public record UserProfile(
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
        Boolean nicknameConfirmed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public Long requireSchoolId() {
        if (schoolId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先完成学校绑定");
        }
        return schoolId;
    }
}
