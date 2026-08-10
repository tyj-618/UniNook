package com.uninook.question;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.QuestionAnswerEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface QuestionAnswerMapper extends BaseMapper<QuestionAnswerEntity> {

    String ANSWER_SELECT = """
            SELECT qa.id, qa.question_id AS questionId, qa.comment_id AS commentId,
                   c.post_id AS postId, c.parent_comment_id AS parentCommentId,
                   qa.answerer_id AS answererId, u.nickname AS answererNickname, u.avatar_url AS answererAvatarUrl,
                   c.content, qa.status, qa.reviewed_by AS reviewedBy,
                   qa.created_at AS createdAt, qa.reviewed_at AS reviewedAt
            FROM question_answer qa
            JOIN `comment` c ON c.id = qa.comment_id AND c.status = 0
            JOIN `user` u ON u.id = qa.answerer_id
            """;

    @Select(ANSWER_SELECT + " WHERE qa.question_id = #{questionId} AND qa.status <> 'WITHDRAWN' ORDER BY CASE qa.status WHEN 'ACCEPTED' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END, qa.created_at ASC, qa.id ASC")
    List<QuestionAnswerItem> selectByQuestionId(@Param("questionId") Long questionId);

    @Select(ANSWER_SELECT + " WHERE qa.question_id = #{questionId} AND qa.status = 'ACCEPTED' ORDER BY qa.reviewed_at ASC, qa.id ASC")
    List<QuestionAnswerItem> selectAcceptedByQuestionId(@Param("questionId") Long questionId);

    @Select(ANSWER_SELECT + " WHERE qa.id = #{answerId} AND qa.question_id = #{questionId} AND qa.status <> 'WITHDRAWN'")
    QuestionAnswerItem selectByIdAndQuestionId(@Param("answerId") Long answerId, @Param("questionId") Long questionId);

    default Optional<QuestionAnswerItem> findByIdAndQuestionId(Long answerId, Long questionId) {
        return Optional.ofNullable(selectByIdAndQuestionId(answerId, questionId));
    }

    @Select(ANSWER_SELECT + " WHERE qa.id = #{answerId} AND qa.status = 'ACCEPTED'")
    QuestionAnswerItem selectAcceptedById(@Param("answerId") Long answerId);

    default Optional<QuestionAnswerItem> findAcceptedById(Long answerId) {
        return Optional.ofNullable(selectAcceptedById(answerId));
    }

    @Update("UPDATE question_answer SET status = 'ACCEPTED', reviewed_by = #{reviewerId}, reviewed_at = CURRENT_TIMESTAMP WHERE id = #{answerId} AND question_id = #{questionId} AND status = 'PENDING'")
    int accept(@Param("questionId") Long questionId, @Param("answerId") Long answerId, @Param("reviewerId") Long reviewerId);

    @Update("UPDATE question_answer SET status = 'REJECTED', reviewed_by = #{reviewerId}, reviewed_at = CURRENT_TIMESTAMP WHERE id = #{answerId} AND question_id = #{questionId} AND status = 'PENDING'")
    int reject(@Param("questionId") Long questionId, @Param("answerId") Long answerId, @Param("reviewerId") Long reviewerId);

    @Select("SELECT DISTINCT question_id FROM question_answer WHERE comment_id = #{commentId} AND status = 'ACCEPTED'")
    List<Long> selectAcceptedQuestionIdsByCommentId(@Param("commentId") Long commentId);

    @Update("UPDATE question_answer SET status = 'WITHDRAWN', updated_at = CURRENT_TIMESTAMP WHERE comment_id = #{commentId} AND status <> 'WITHDRAWN'")
    void withdrawByCommentId(@Param("commentId") Long commentId);

    @Select("SELECT COUNT(*) FROM question_answer WHERE comment_id = #{commentId} AND status = 'ACCEPTED'")
    long countAcceptedByCommentId(@Param("commentId") Long commentId);

    @Select("SELECT COUNT(*) FROM question_answer qa JOIN `comment` c ON c.id = qa.comment_id WHERE qa.status = 'ACCEPTED' AND (c.id = #{rootCommentId} OR c.root_comment_id = #{rootCommentId})")
    long countAcceptedByRootCommentId(@Param("rootCommentId") Long rootCommentId);

    @Select("SELECT DISTINCT qa.question_id FROM question_answer qa JOIN `comment` c ON c.id = qa.comment_id WHERE qa.status = 'ACCEPTED' AND (c.id = #{rootCommentId} OR c.root_comment_id = #{rootCommentId})")
    List<Long> selectAcceptedQuestionIdsByRootCommentId(@Param("rootCommentId") Long rootCommentId);

    @Update("UPDATE question_answer SET status = 'WITHDRAWN', updated_at = CURRENT_TIMESTAMP WHERE status <> 'WITHDRAWN' AND comment_id IN (SELECT id FROM `comment` WHERE id = #{rootCommentId} OR root_comment_id = #{rootCommentId})")
    void withdrawByRootCommentId(@Param("rootCommentId") Long rootCommentId);

    @Delete("DELETE FROM question_answer WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
