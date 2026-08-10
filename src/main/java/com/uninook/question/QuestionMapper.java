package com.uninook.question;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.QuestionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface QuestionMapper extends BaseMapper<QuestionEntity> {

    String QUESTION_SELECT = """
            SELECT q.id, q.source_type AS sourceType, q.source_id AS sourceId,
                   CASE WHEN q.source_type = 'POST' THEN p.id ELSE c.post_id END AS sourcePostId,
                   CASE WHEN q.source_type = 'POST' THEN p.title ELSE CONCAT('评论：', LEFT(c.content, 80)) END AS sourcePreview,
                   q.asker_id AS askerId, u.nickname AS askerNickname, u.avatar_url AS askerAvatarUrl,
                   q.question_text AS questionText, q.status,
                   (SELECT COUNT(*) FROM question_answer qa WHERE qa.question_id = q.id AND qa.status = 'ACCEPTED') AS approvedAnswerCount,
                   q.subscriber_count AS subscriberCount,
                   q.created_at AS createdAt, q.updated_at AS updatedAt
            FROM question q
            JOIN `user` u ON u.id = q.asker_id
            LEFT JOIN post p ON q.source_type = 'POST' AND p.id = q.source_id
            LEFT JOIN `comment` c ON q.source_type = 'COMMENT' AND c.id = q.source_id
            """;

    String NORMAL_SOURCE_CONDITION = """
            (q.source_type = 'POST' AND p.status = 0)
            OR (q.source_type = 'COMMENT' AND c.status = 0
                AND EXISTS (SELECT 1 FROM post cp WHERE cp.id = c.post_id AND cp.status = 0))
            """;

    @Select(QUESTION_SELECT + " WHERE q.source_type = #{sourceType} AND q.source_id = #{sourceId} AND (" + NORMAL_SOURCE_CONDITION + ") ORDER BY q.created_at ASC, q.id ASC LIMIT 1")
    QuestionItem selectBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    default Optional<QuestionItem> findBySource(QuestionSourceType sourceType, Long sourceId) {
        return Optional.ofNullable(selectBySource(sourceType.name(), sourceId));
    }

    @Select("<script>" + QUESTION_SELECT
            + " WHERE q.source_type = #{sourceType}"
            + " AND q.source_id IN <foreach collection='sourceIds' item='sourceId' open='(' separator=',' close=')'>#{sourceId}</foreach>"
            + " AND (" + NORMAL_SOURCE_CONDITION + ")"
            + " ORDER BY q.source_id ASC, q.created_at ASC, q.id ASC"
            + "</script>")
    List<QuestionItem> selectBySources(@Param("sourceType") String sourceType,
                                       @Param("sourceIds") List<Long> sourceIds);

    default List<QuestionItem> findBySources(QuestionSourceType sourceType, List<Long> sourceIds) {
        return sourceIds.isEmpty() ? List.of() : selectBySources(sourceType.name(), sourceIds);
    }

    @Select(QUESTION_SELECT + " WHERE q.id = #{questionId} AND (" + NORMAL_SOURCE_CONDITION + ")")
    QuestionItem selectQuestionItemById(@Param("questionId") Long questionId);

    default Optional<QuestionItem> findById(Long questionId) {
        return Optional.ofNullable(selectQuestionItemById(questionId));
    }

    @Select("SELECT COUNT(*) FROM question WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    long countBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    default boolean existsBySource(QuestionSourceType sourceType, Long sourceId) {
        return countBySource(sourceType.name(), sourceId) > 0;
    }

    @Select("SELECT COUNT(*) FROM question_subscription WHERE question_id = #{questionId} AND user_id = #{userId}")
    long countSubscription(@Param("questionId") Long questionId, @Param("userId") Long userId);

    default boolean hasSubscription(Long questionId, Long userId) {
        return countSubscription(questionId, userId) > 0;
    }

    @Select("<script>SELECT question_id FROM question_subscription WHERE user_id = #{userId}"
            + " AND question_id IN <foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>#{questionId}</foreach>"
            + "</script>")
    List<Long> findSubscribedQuestionIds(@Param("userId") Long userId, @Param("questionIds") List<Long> questionIds);

    @Insert("INSERT INTO question_subscription (question_id, user_id) VALUES (#{questionId}, #{userId})")
    void insertSubscription(@Param("questionId") Long questionId, @Param("userId") Long userId);

    @Delete("DELETE FROM question_subscription WHERE question_id = #{questionId} AND user_id = #{userId}")
    int deleteSubscription(@Param("questionId") Long questionId, @Param("userId") Long userId);

    @Update("UPDATE question SET subscriber_count = subscriber_count + 1 WHERE id = #{questionId}")
    void increaseSubscriberCount(@Param("questionId") Long questionId);

    @Update("UPDATE question SET subscriber_count = GREATEST(subscriber_count - 1, 0) WHERE id = #{questionId}")
    void decreaseSubscriberCount(@Param("questionId") Long questionId);

    @Select("SELECT COUNT(*) FROM question q LEFT JOIN post p ON q.source_type = 'POST' AND p.id = q.source_id LEFT JOIN `comment` c ON q.source_type = 'COMMENT' AND c.id = q.source_id WHERE q.asker_id = #{userId} AND (" + NORMAL_SOURCE_CONDITION + ")")
    long countAskedByUserId(@Param("userId") Long userId);

    @Select(QUESTION_SELECT + " WHERE q.asker_id = #{userId} AND (" + NORMAL_SOURCE_CONDITION + ") ORDER BY q.updated_at DESC, q.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<QuestionItem> findAskedByUserId(@Param("userId") Long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*)
            FROM question_subscription qs
            JOIN question q ON q.id = qs.question_id
            LEFT JOIN post p ON q.source_type = 'POST' AND p.id = q.source_id
            LEFT JOIN `comment` c ON q.source_type = 'COMMENT' AND c.id = q.source_id
            WHERE qs.user_id = #{userId} AND (""" + NORMAL_SOURCE_CONDITION + ")")
    long countSubscribedByUserId(@Param("userId") Long userId);

    @Select(QUESTION_SELECT
            + " JOIN question_subscription qs ON qs.question_id = q.id"
            + " WHERE qs.user_id = #{userId} AND (" + NORMAL_SOURCE_CONDITION + ")"
            + " ORDER BY q.updated_at DESC, q.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<QuestionItem> findSubscribedByUserId(@Param("userId") Long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT user_id FROM question_subscription WHERE question_id = #{questionId}")
    List<Long> findSubscriberIds(@Param("questionId") Long questionId);

    @Select("""
            SELECT q.id AS questionId, q.source_type AS sourceType,
                   CASE WHEN q.source_type = 'COMMENT' THEN q.source_id ELSE NULL END AS sourceCommentId
            FROM question q
            LEFT JOIN `comment` c ON q.source_type = 'COMMENT' AND c.id = q.source_id
            WHERE (q.source_type = 'POST' AND q.source_id = #{postId})
               OR (q.source_type = 'COMMENT' AND c.post_id = #{postId})
            """)
    List<QuestionSourceCleanupItem> findByPostId(@Param("postId") Long postId);

    @Delete("DELETE FROM question_subscription WHERE question_id = #{questionId}")
    int deleteSubscriptionsByQuestionId(@Param("questionId") Long questionId);

    @Update("UPDATE question SET status = 'COMPLETED', accepted_answer_id = NULL, last_answer_at = CURRENT_TIMESTAMP WHERE id = #{questionId} AND status = 'OPEN'")
    int complete(@Param("questionId") Long questionId);

    @Update("UPDATE question SET status = 'OPEN', updated_at = CURRENT_TIMESTAMP WHERE id = #{questionId} AND status = 'COMPLETED'")
    int reopen(@Param("questionId") Long questionId);

    @Update("UPDATE question SET last_answer_at = CURRENT_TIMESTAMP WHERE id = #{questionId}")
    void touchAnswerTime(@Param("questionId") Long questionId);
}
