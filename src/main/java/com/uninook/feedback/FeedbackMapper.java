package com.uninook.feedback;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackMapper {

    @Select("SELECT COUNT(*) FROM feedback WHERE user_id = #{userId} AND request_id = #{requestId}")
    long countByUserAndRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Insert("""
            INSERT INTO feedback (user_id, request_id, rating, comment, question_text)
            VALUES (#{userId}, #{requestId}, #{rating}, #{comment}, #{question})
            """)
    void insert(@Param("userId") Long userId, @Param("requestId") String requestId,
                @Param("rating") String rating, @Param("comment") String comment, @Param("question") String question);

    @Update("""
            UPDATE feedback
            SET rating = #{rating}, comment = #{comment}, question_text = #{question}, created_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND request_id = #{requestId}
            """)
    void update(@Param("userId") Long userId, @Param("requestId") String requestId,
                @Param("rating") String rating, @Param("comment") String comment, @Param("question") String question);
}
