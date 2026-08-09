package com.campuscircle.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
        @NotNull QuestionSourceType sourceType,
        @NotNull Long sourceId,
        @NotBlank @Size(max = 300) String questionText
) {
}
