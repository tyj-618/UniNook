package com.campuscircle.ai;

import java.time.LocalDateTime;
import java.util.List;

record PostSearchDocument(
        Long postId,
        Long schoolId,
        Long categoryId,
        String title,
        String content,
        String categoryName,
        String schoolName,
        String campusName,
        String city,
        String searchText,
        LocalDateTime updatedAt,
        List<Float> embedding
) {
}
