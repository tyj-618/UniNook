package com.uninook.user;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 32, message = "昵称长度不能超过 32 位")
        String nickname,

        @Size(max = 255, message = "个人简介长度不能超过 255 位")
        String bio,

        Long schoolId
) {
}
