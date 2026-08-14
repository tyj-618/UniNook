package com.uninook.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessReportRequest(
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String adminNote
) {
}
