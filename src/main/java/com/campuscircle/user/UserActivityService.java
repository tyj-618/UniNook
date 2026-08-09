package com.campuscircle.user;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.common.PageResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserActivityService {

    private final CurrentUserService currentUserService;
    private final UserActivityMapper userActivityMapper;

    public UserActivityService(CurrentUserService currentUserService, UserActivityMapper userActivityMapper) {
        this.currentUserService = currentUserService;
        this.userActivityMapper = userActivityMapper;
    }

    public PageResponse<MyLikeResponse> listMyLikes(String authorization, int page, int size) {
        Long userId = currentUserService.requireUserId(authorization);
        long total = userActivityMapper.countMyLikes(userId);
        List<MyLikeResponse> records = userActivityMapper.findMyLikes(userId, size, (page - 1) * size)
                .stream()
                .map(MyLikeResponse::from)
                .toList();
        return PageResponse.of(page, size, total, records);
    }
}
