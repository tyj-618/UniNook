package com.uninook.admin;

import java.time.LocalDateTime;

public record AdminPostListItem(
        Long id,
        String title,
        int status,
        Long authorId,
        String authorNickname,
        String schoolName,
        String campusName,
        LocalDateTime createdAt
) {
}
