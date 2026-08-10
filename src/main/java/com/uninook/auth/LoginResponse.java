package com.uninook.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginResponse(
        String token,
        long expiresIn,
        @JsonIgnore String refreshToken,
        @JsonIgnore long refreshExpiresIn,
        UserSummary user
) {
}
