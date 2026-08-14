package com.uninook.admin;

import java.time.LocalDateTime;

public record AdminReportListItem(
        Long id,
        Long reporterId,
        String reporterNickname,
        String targetType,
        Long targetId,
        String reason,
        String status,
        Long adminId,
        String adminNickname,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
}
