package com.uninook.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.CommentLikeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

public interface CommentLikeMapper extends BaseMapper<CommentLikeEntity> {

    @Select("SELECT id, status FROM comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    CommentLikeRecord selectByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    default Optional<CommentLikeRecord> findByCommentIdAndUserId(Long commentId, Long userId) {
        return Optional.ofNullable(selectByCommentIdAndUserId(commentId, userId));
    }

    default void saveLike(Long commentId, Long userId) {
        CommentLikeEntity like = new CommentLikeEntity();
        like.setCommentId(commentId);
        like.setUserId(userId);
        like.setStatus(0);
        insert(like);
    }

    @Update("UPDATE comment_like SET status = 0 WHERE id = #{likeId}")
    void activateLike(@Param("likeId") Long likeId);

    @Update("UPDATE comment_like SET status = 1 WHERE id = #{likeId}")
    void cancelLike(@Param("likeId") Long likeId);

    @Update("UPDATE `comment` SET like_count = like_count + 1 WHERE id = #{commentId} AND status = 0")
    void increaseLikeCount(@Param("commentId") Long commentId);

    @Update("UPDATE `comment` SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{commentId} AND status = 0")
    void decreaseLikeCount(@Param("commentId") Long commentId);

    @Select("SELECT like_count FROM `comment` WHERE id = #{commentId}")
    Integer selectLikeCount(@Param("commentId") Long commentId);

    default int findLikeCount(Long commentId) {
        Integer count = selectLikeCount(commentId);
        return count == null ? 0 : count;
    }
}
