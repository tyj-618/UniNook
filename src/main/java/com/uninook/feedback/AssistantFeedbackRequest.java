package com.uninook.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantFeedbackRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Size(max = 16) String rating,
        @Size(max = 500) String comment,
        @Size(max = 500) String question
) {
}
