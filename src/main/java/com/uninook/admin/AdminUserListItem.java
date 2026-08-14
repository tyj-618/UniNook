package com.uninook.admin;

import java.time.LocalDateTime;

public record AdminUserListItem(
        Long id,
        String username,
        String nickname,
        int role,
        int status,
        String schoolName,
        String campusName,
        LocalDateTime createdAt
) {
}
