package com.uninook.post;

import java.time.LocalDateTime;

public record FeedCursor(
        LocalDateTime createdAt,
        Long id
) {
}
