package com.campuscircle.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuscircle.common.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserActivityMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT pl.id
                FROM post_like pl
                JOIN post p ON p.id = pl.post_id AND p.status = 0
                WHERE pl.user_id = #{userId} AND pl.status = 0
                UNION ALL
                SELECT cl.id
                FROM comment_like cl
                JOIN `comment` c ON c.id = cl.comment_id AND c.status = 0
                JOIN post p ON p.id = c.post_id AND p.status = 0
                WHERE cl.user_id = #{userId} AND cl.status = 0
            ) liked_content
            """)
    long countMyLikes(@Param("userId") Long userId);

    @Select("""
            SELECT target_type AS targetType,
                   post_id AS postId,
                   comment_id AS commentId,
                   post_title AS postTitle,
                   target_content AS targetContent,
                   created_at AS createdAt
            FROM (
                SELECT 'POST' AS target_type,
                       p.id AS post_id,
                       NULL AS comment_id,
                       p.title AS post_title,
                       p.content AS target_content,
                       pl.updated_at AS created_at
                FROM post_like pl
                JOIN post p ON p.id = pl.post_id AND p.status = 0
                WHERE pl.user_id = #{userId} AND pl.status = 0
                UNION ALL
                SELECT 'COMMENT' AS target_type,
                       p.id AS post_id,
                       c.id AS comment_id,
                       p.title AS post_title,
                       c.content AS target_content,
                       cl.updated_at AS created_at
                FROM comment_like cl
                JOIN `comment` c ON c.id = cl.comment_id AND c.status = 0
                JOIN post p ON p.id = c.post_id AND p.status = 0
                WHERE cl.user_id = #{userId} AND cl.status = 0
            ) liked_content
            ORDER BY created_at DESC, post_id DESC, comment_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MyLikeItem> findMyLikes(@Param("userId") Long userId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);
}
