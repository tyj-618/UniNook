package com.uninook.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotBlank @Size(max = 16) String targetType,
        @NotNull Long targetId,
        @NotBlank @Size(max = 500) String reason
) {
}
