package com.campuscircle.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuscircle.common.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {

    @Select("""
            SELECT id, event_type AS eventType, payload, status, retry_count AS retryCount,
                   next_attempt_at AS nextAttemptAt
            FROM event_outbox
            WHERE status = 0 AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at, id
            LIMIT #{limit}
            """)
    List<OutboxEventEntity> findPending(@Param("limit") int limit);

    @Update("UPDATE event_outbox SET status = 1, published_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 0")
    int markPublished(@Param("id") String id);

    @Update("""
            UPDATE event_outbox
            SET retry_count = retry_count + 1,
                next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{retryDelaySeconds} SECOND)
            WHERE id = #{id} AND status = 0
            """)
    int scheduleRetry(@Param("id") String id, @Param("retryDelaySeconds") int retryDelaySeconds);
}
