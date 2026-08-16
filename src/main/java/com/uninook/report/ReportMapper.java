package com.uninook.report;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportMapper {

    @Select("SELECT user_id FROM post WHERE id = #{targetId} AND status = 0")
    Long findNormalPostAuthorId(@Param("targetId") Long targetId);

    @Select("SELECT user_id FROM `comment` WHERE id = #{targetId} AND status = 0")
    Long findNormalCommentAuthorId(@Param("targetId") Long targetId);

    @Select("""
            SELECT COUNT(*) FROM report
            WHERE reporter_id = #{reporterId} AND target_type = #{targetType}
              AND target_id = #{targetId} AND status = 'PENDING'
            """)
    long countPendingReports(@Param("reporterId") Long reporterId, @Param("targetType") String targetType,
                             @Param("targetId") Long targetId);

    @Insert("""
            INSERT INTO report (reporter_id, target_type, target_id, reason, status)
            VALUES (#{report.reporterId}, #{report.targetType}, #{report.targetId}, #{report.reason}, 'PENDING')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "report.id")
    void insert(@Param("report") ReportRecord report);
}
