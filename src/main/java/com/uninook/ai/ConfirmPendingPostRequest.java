package com.uninook.ai;

import jakarta.validation.constraints.NotNull;

public record ConfirmPendingPostRequest(
        @NotNull(message = "分类不能为空")
        Long categoryId
) {
}
