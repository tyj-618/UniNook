package com.campuscircle.user;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;
import com.campuscircle.school.SchoolService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static java.time.LocalTime.MIN;

@Service
public class UserService {

    private static final int MONTHLY_SCHOOL_CHANGE_LIMIT = 5;

    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;
    private final SchoolService schoolService;
    private final AvatarStorageService avatarStorageService;

    public UserService(CurrentUserService currentUserService, UserMapper userMapper, SchoolService schoolService,
                       AvatarStorageService avatarStorageService) {
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
        this.schoolService = schoolService;
        this.avatarStorageService = avatarStorageService;
    }

    public UserProfileResponse getCurrentUser(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile userProfile = findExistingUser(currentUserId);
        return UserProfileResponse.from(userProfile);
    }

    public SchoolChangeQuotaResponse getSchoolChangeQuota(String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        return getSchoolChangeQuota(currentUserId);
    }

    @Transactional
    public UserProfileResponse updateCurrentUser(String authorization, UpdateUserProfileRequest request) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile oldProfile = findExistingUser(currentUserId);

        String nickname = cleanOrDefault(request.nickname(), oldProfile.nickname());
        String bio = cleanOrDefault(request.bio(), oldProfile.bio());
        Long schoolId = request.schoolId() == null ? oldProfile.schoolId() : request.schoolId();
        boolean nicknameConfirmed = Boolean.TRUE.equals(oldProfile.nicknameConfirmed())
                || (request.nickname() != null && !request.nickname().trim().isEmpty());

        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称不能为空");
        }

        if (schoolId != null) {
            schoolService.findEnabledSchool(schoolId);
        }

        boolean schoolChanged = oldProfile.schoolId() != null && !oldProfile.schoolId().equals(schoolId);
        if (schoolChanged) {
            SchoolChangeQuotaResponse quota = getSchoolChangeQuota(currentUserId);
            if (quota.remaining() == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "本月修改学校次数已达上限，请下月再试");
            }
        }
        userMapper.updateProfile(currentUserId, nickname, bio, schoolId, nicknameConfirmed);
        if (schoolChanged) {
            userMapper.insertSchoolChange(currentUserId, oldProfile.schoolId(), schoolId);
        }

        UserProfile updatedProfile = findExistingUser(currentUserId);
        return UserProfileResponse.from(updatedProfile);
    }

    @Transactional
    public AvatarUploadResponse uploadAvatar(String authorization, MultipartFile file) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile oldProfile = findExistingUser(currentUserId);
        String avatarUrl = avatarStorageService.store(currentUserId, file);
        userMapper.updateAvatarUrl(currentUserId, avatarUrl);
        avatarStorageService.deleteIfManaged(oldProfile.avatarUrl());
        return new AvatarUploadResponse(avatarUrl);
    }

    public PublicUserProfileResponse getPublicUserProfile(Long userId) {
        UserProfile userProfile = findExistingUser(userId);
        long postCount = userMapper.countNormalPostsByUserId(userId);
        long commentCount = userMapper.countNormalCommentsByUserId(userId);
        long likeCount = userMapper.countActiveLikesByUserId(userId);
        return new PublicUserProfileResponse(
                userProfile.id(),
                userProfile.username(),
                userProfile.nickname(),
                userProfile.schoolId(),
                userProfile.universityId(),
                userProfile.schoolName(),
                userProfile.campusName(),
                userProfile.schoolCity(),
                userProfile.avatarUrl(),
                userProfile.bio(),
                postCount,
                commentCount,
                likeCount,
                userProfile.createdAt()
        );
    }

    private UserProfile findExistingUser(Long userId) {
        return userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private SchoolChangeQuotaResponse getSchoolChangeQuota(Long userId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atTime(MIN);
        int used = Math.toIntExact(userMapper.countSchoolChangesSince(userId, monthStart));
        int remaining = Math.max(MONTHLY_SCHOOL_CHANGE_LIMIT - used, 0);
        LocalDate resetsOn = YearMonth.from(monthStart).plusMonths(1).atDay(1);
        return new SchoolChangeQuotaResponse(used, MONTHLY_SCHOOL_CHANGE_LIMIT, remaining, resetsOn);
    }

    private String cleanOrDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        String cleanedValue = value.trim();
        return cleanedValue.isEmpty() ? null : cleanedValue;
    }
}
