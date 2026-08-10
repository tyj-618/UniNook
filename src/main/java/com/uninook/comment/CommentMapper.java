package com.uninook.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.CommentEntity;
import com.uninook.post.PageQueryResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface CommentMapper extends BaseMapper<CommentEntity> {

    @Select("SELECT COUNT(*) FROM post WHERE id = #{postId} AND status = 0")
    long countNormalPost(@Param("postId") Long postId);

    default boolean existsNormalPost(Long postId) {
        return countNormalPost(postId) > 0;
    }

    default Long saveComment(Long postId, Long userId, Long authorSchoolId, String authorSchoolName,
                             String authorCampusName,
                             Long rootCommentId, Long parentCommentId, Long replyToUserId, String content) {
        CommentEntity comment = new CommentEntity();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setAuthorSchoolId(authorSchoolId);
        comment.setAuthorSchoolName(authorSchoolName);
        comment.setAuthorCampusName(authorCampusName);
        comment.setRootCommentId(rootCommentId);
        comment.setParentCommentId(parentCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content.trim());
        insert(comment);
        return comment.getId();
    }

    @Update("""
            UPDATE post_stat
            SET comment_count = comment_count + 1,
                hot_score = hot_score + 3
            WHERE post_id = #{postId}
            """)
    void increaseCommentCount(@Param("postId") Long postId);

    @Update("""
            UPDATE post_stat
            SET comment_count = GREATEST(comment_count - #{count}, 0),
                hot_score = GREATEST(hot_score - #{count} * 3, 0)
            WHERE post_id = #{postId}
            """)
    void decreaseCommentCount(@Param("postId") Long postId, @Param("count") long count);

    @Select("SELECT COUNT(*) FROM `comment` WHERE post_id = #{postId} AND status = 0")
    long countCommentsByPostId(@Param("postId") Long postId);

    @Select("""
            SELECT c.id, c.post_id AS postId, c.user_id AS userId,
                   c.root_comment_id AS rootCommentId, c.parent_comment_id AS parentCommentId,
                   c.reply_to_user_id AS replyToUserId, c.content,
                   c.like_count AS likeCount,
                   u.nickname AS authorNickname, u.avatar_url AS authorAvatarUrl,
                   c.author_school_name AS authorSchoolName, c.author_campus_name AS authorCampusName,
                   reply_user.nickname AS replyToNickname,
                   CASE WHEN comment_like.id IS NULL THEN FALSE ELSE TRUE END AS liked,
                   c.created_at AS createdAt
            FROM `comment` c
            JOIN `user` u ON c.user_id = u.id
            LEFT JOIN `user` reply_user ON c.reply_to_user_id = reply_user.id
            LEFT JOIN comment_like ON comment_like.comment_id = c.id
                AND comment_like.user_id = #{currentUserId} AND comment_like.status = 0
            WHERE c.post_id = #{postId} AND c.status = 0
            ORDER BY COALESCE(c.root_comment_id, c.id) ASC,
                     CASE WHEN c.root_comment_id IS NULL THEN 0 ELSE 1 END ASC,
                     c.created_at ASC, c.id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<CommentPageItem> selectCommentsByPostId(@Param("postId") Long postId,
                                                 @Param("currentUserId") Long currentUserId,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);

    default PageQueryResult<CommentPageItem> findCommentsByPostId(Long postId, Long currentUserId, int page, int size) {
        long total = countCommentsByPostId(postId);
        List<CommentPageItem> records = selectCommentsByPostId(postId, currentUserId, size, (page - 1) * size);
        return new PageQueryResult<>(total, records);
    }

    @Select("""
            SELECT c.id, c.post_id AS postId, c.user_id AS userId,
                   c.root_comment_id AS rootCommentId, c.parent_comment_id AS parentCommentId,
                   c.reply_to_user_id AS replyToUserId, c.content,
                   c.like_count AS likeCount,
                   u.nickname AS authorNickname, u.avatar_url AS authorAvatarUrl,
                   c.author_school_name AS authorSchoolName, c.author_campus_name AS authorCampusName,
                   reply_user.nickname AS replyToNickname,
                   CASE WHEN comment_like.id IS NULL THEN FALSE ELSE TRUE END AS liked,
                   c.created_at AS createdAt
            FROM `comment` c
            JOIN `user` u ON c.user_id = u.id
            LEFT JOIN `user` reply_user ON c.reply_to_user_id = reply_user.id
            LEFT JOIN comment_like ON comment_like.comment_id = c.id
                AND comment_like.user_id = #{currentUserId} AND comment_like.status = 0
            WHERE c.post_id = #{postId} AND c.status = 0
              AND (c.id = COALESCE((SELECT root_comment_id FROM `comment` WHERE id = #{focusCommentId}), #{focusCommentId})
                   OR c.root_comment_id = COALESCE((SELECT root_comment_id FROM `comment` WHERE id = #{focusCommentId}), #{focusCommentId}))
            ORDER BY CASE WHEN c.root_comment_id IS NULL THEN 0 ELSE 1 END ASC, c.created_at ASC, c.id ASC
            """)
    List<CommentPageItem> selectCommentThreadByTarget(@Param("postId") Long postId,
                                                       @Param("currentUserId") Long currentUserId,
                                                       @Param("focusCommentId") Long focusCommentId);

    default PageQueryResult<CommentPageItem> findCommentThreadByTarget(Long postId, Long currentUserId, Long focusCommentId) {
        List<CommentPageItem> records = selectCommentThreadByTarget(postId, currentUserId, focusCommentId);
        return new PageQueryResult<>(records.size(), records);
    }

    @Select("SELECT COUNT(*) FROM `comment` WHERE user_id = #{userId} AND status = 0")
    long countCommentsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT c.id, c.post_id AS postId, p.title AS postTitle, c.content,
                   c.created_at AS createdAt
            FROM `comment` c
            JOIN post p ON c.post_id = p.id
            WHERE c.user_id = #{userId} AND c.status = 0
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MyCommentItem> selectCommentsByUserId(@Param("userId") Long userId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    default PageQueryResult<MyCommentItem> findCommentsByUserId(Long userId, int page, int size) {
        long total = countCommentsByUserId(userId);
        List<MyCommentItem> records = selectCommentsByUserId(userId, size, (page - 1) * size);
        return new PageQueryResult<>(total, records);
    }

    @Select("""
            SELECT c.id, c.post_id AS postId, c.user_id AS userId, p.user_id AS postAuthorId,
                   c.root_comment_id AS rootCommentId, c.parent_comment_id AS parentCommentId,
                   c.reply_to_user_id AS replyToUserId,
                   c.content, c.status, c.created_at AS createdAt, c.updated_at AS updatedAt
            FROM `comment` c
            JOIN post p ON c.post_id = p.id
            WHERE c.id = #{commentId}
            """)
    CommentDetail selectDetailById(@Param("commentId") Long commentId);

    default Optional<CommentDetail> findDetailById(Long commentId) {
        return Optional.ofNullable(selectDetailById(commentId));
    }

    @Select("SELECT COUNT(*) FROM `comment` WHERE status = 0 AND (id = #{rootCommentId} OR root_comment_id = #{rootCommentId})")
    long countNormalCommentThread(@Param("rootCommentId") Long rootCommentId);

    @Update("UPDATE `comment` SET status = 1 WHERE status = 0 AND (id = #{rootCommentId} OR root_comment_id = #{rootCommentId})")
    void softDeleteCommentThread(@Param("rootCommentId") Long rootCommentId);

    @Update("UPDATE `comment` SET status = 1 WHERE id = #{commentId} AND status = 0")
    void softDeleteComment(@Param("commentId") Long commentId);
}
