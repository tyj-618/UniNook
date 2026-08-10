package com.uninook.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.time.LocalDateTime;

public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT u.id, u.username, u.nickname, u.school_id AS schoolId,
                   s.university_id AS universityId, COALESCE(uni.name, s.name) AS schoolName,
                   s.campus_name AS campusName, s.city AS schoolCity,
                   u.avatar_url AS avatarUrl, u.bio, u.role, u.status,
                   u.nickname_confirmed AS nicknameConfirmed,
                   u.created_at AS createdAt, u.updated_at AS updatedAt
            FROM `user` u
            LEFT JOIN school s ON u.school_id = s.id
            LEFT JOIN university uni ON s.university_id = uni.id
            WHERE u.id = #{userId}
            """)
    Optional<UserProfile> findProfileById(@Param("userId") Long userId);

    @Update("""
            UPDATE `user`
            SET nickname = #{nickname}, bio = #{bio}, school_id = #{schoolId},
                nickname_confirmed = #{nicknameConfirmed}
            WHERE id = #{userId}
            """)
    void updateProfile(
            @Param("userId") Long userId,
            @Param("nickname") String nickname,
            @Param("bio") String bio,
            @Param("schoolId") Long schoolId,
            @Param("nicknameConfirmed") boolean nicknameConfirmed);

    @Update("UPDATE `user` SET avatar_url = #{avatarUrl} WHERE id = #{userId}")
    void updateAvatarUrl(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl);

    @Select("SELECT COUNT(*) FROM school_change_log WHERE user_id = #{userId} AND created_at >= #{monthStart}")
    long countSchoolChangesSince(@Param("userId") Long userId, @Param("monthStart") LocalDateTime monthStart);

    @Insert("INSERT INTO school_change_log (user_id, from_school_id, to_school_id) VALUES (#{userId}, #{fromSchoolId}, #{toSchoolId})")
    void insertSchoolChange(@Param("userId") Long userId, @Param("fromSchoolId") Long fromSchoolId,
                            @Param("toSchoolId") Long toSchoolId);

    @Select("SELECT COUNT(*) FROM post WHERE user_id = #{userId} AND status = 0")
    long countNormalPostsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM `comment` WHERE user_id = #{userId} AND status = 0")
    long countNormalCommentsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM post_like WHERE user_id = #{userId} AND status = 0)
              + (SELECT COUNT(*) FROM comment_like WHERE user_id = #{userId} AND status = 0)
            """)
    long countActiveLikesByUserId(@Param("userId") Long userId);
}
