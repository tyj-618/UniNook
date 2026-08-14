package com.uninook.ai;

import com.uninook.school.CampusScope;
import com.uninook.user.UserProfile;

public record ToolExecutionContext(Long userId, UserProfile userProfile, CampusScope scope) {
}
