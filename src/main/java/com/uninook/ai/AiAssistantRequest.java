package com.uninook.ai;

import com.uninook.school.CampusScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiAssistantRequest(
        @NotBlank @Size(max = 200) String question,
        CampusScope scope,
        @Min(1) @Max(50) Double radiusKm,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,100}$") String sessionId
) {
}
