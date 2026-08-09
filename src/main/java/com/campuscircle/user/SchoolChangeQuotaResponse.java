package com.campuscircle.user;

import java.time.LocalDate;

public record SchoolChangeQuotaResponse(
        int used,
        int limit,
        int remaining,
        LocalDate resetsOn
) {
}
