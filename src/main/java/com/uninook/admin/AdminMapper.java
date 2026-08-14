package com.uninook.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AdminMapper extends BaseMapper<UserEntity> {

    @Select("SELECT COUNT(*) FROM post WHERE id = #{postId}")
    long countPostById(@Param("postId") Long postId);

    default boolean existsPost(Long postId) {
        return countPostById(postId) > 0;
    }

    @Update("UPDATE post SET status = #{status} WHERE id = #{postId}")
    void updatePostStatus(@Param("postId") Long postId, @Param("status") int status);

    @Select("SELECT COUNT(*) FROM `user` WHERE id = #{userId}")
    long countUserById(@Param("userId") Long userId);

    default boolean existsUser(Long userId) {
        return countUserById(userId) > 0;
    }

    @Update("UPDATE `user` SET status = #{status} WHERE id = #{userId}")
    void updateUserStatus(@Param("userId") Long userId, @Param("status") int status);

    @SelectProvider(type = AdminSqlProvider.class, method = "countPosts")
    long countPosts(@Param("keyword") String keyword, @Param("status") Integer status);

    @SelectProvider(type = AdminSqlProvider.class, method = "selectPosts")
    List<AdminPostListItem> selectPosts(@Param("keyword") String keyword, @Param("status") Integer status,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @SelectProvider(type = AdminSqlProvider.class, method = "countUsers")
    long countUsers(@Param("keyword") String keyword, @Param("status") Integer status);

    @SelectProvider(type = AdminSqlProvider.class, method = "selectUsers")
    List<AdminUserListItem> selectUsers(@Param("keyword") String keyword, @Param("status") Integer status,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Insert("""
            INSERT INTO admin_action_log (admin_user_id, target_type, target_id, action)
            VALUES (#{adminUserId}, #{targetType}, #{targetId}, #{action})
            """)
    void insertActionLog(@Param("adminUserId") Long adminUserId, @Param("targetType") String targetType,
                         @Param("targetId") Long targetId, @Param("action") String action);

    @Select("""
            SELECT l.id, l.admin_user_id AS adminUserId, u.nickname AS adminNickname,
                   l.target_type AS targetType, l.target_id AS targetId, l.action, l.created_at AS createdAt
            FROM admin_action_log l
            JOIN `user` u ON u.id = l.admin_user_id
            ORDER BY l.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AdminActionLogItem> selectActionLogs(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM admin_action_log")
    long countActionLogs();

    class AdminSqlProvider {
        public String countPosts(Map<String, Object> params) {
            return "<script>SELECT COUNT(*) FROM post p " + postWhereClause(params) + "</script>";
        }

        public String selectPosts(Map<String, Object> params) {
            return """
                    <script>
                    SELECT p.id, p.title, p.status, p.user_id AS authorId, u.nickname AS authorNickname,
                           s.name AS schoolName, s.campus_name AS campusName, p.created_at AS createdAt
                    FROM post p
                    JOIN `user` u ON u.id = p.user_id
                    JOIN school s ON s.id = p.school_id
                    """ + postWhereClause(params) + " ORDER BY p.id DESC LIMIT #{limit} OFFSET #{offset}</script>";
        }

        public String countUsers(Map<String, Object> params) {
            return "<script>SELECT COUNT(*) FROM `user` u " + userWhereClause(params) + "</script>";
        }

        public String selectUsers(Map<String, Object> params) {
            return """
                    <script>
                    SELECT u.id, u.username, u.nickname, u.role, u.status,
                           s.name AS schoolName, s.campus_name AS campusName, u.created_at AS createdAt
                    FROM `user` u
                    LEFT JOIN school s ON s.id = u.school_id
                    """ + userWhereClause(params) + " ORDER BY u.id DESC LIMIT #{limit} OFFSET #{offset}</script>";
        }

        private String postWhereClause(Map<String, Object> params) {
            StringBuilder where = new StringBuilder(" WHERE 1 = 1");
            if (hasText(params, "keyword")) {
                where.append(" AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%'))");
            }
            if (params.get("status") != null) {
                where.append(" AND p.status = #{status}");
            }
            return where.toString();
        }

        private String userWhereClause(Map<String, Object> params) {
            StringBuilder where = new StringBuilder(" WHERE 1 = 1");
            if (hasText(params, "keyword")) {
                where.append(" AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))");
            }
            if (params.get("status") != null) {
                where.append(" AND u.status = #{status}");
            }
            return where.toString();
        }

        private boolean hasText(Map<String, Object> params, String name) {
            Object value = params.get(name);
            return value instanceof String text && !text.isBlank();
        }
    }
}
