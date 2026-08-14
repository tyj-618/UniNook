package com.uninook.admin;

import java.time.LocalDateTime;

public record AdminActionLogItem(
        Long id,
        Long adminUserId,
        String adminNickname,
        String targetType,
        Long targetId,
        String action,
        LocalDateTime createdAt
) {
}
