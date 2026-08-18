package com.audioagent.outbox.mapper;

import com.audioagent.outbox.entity.OutboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT *
            FROM outbox_event
            WHERE aggregate_type = #{aggregateType}
              AND aggregate_id = #{aggregateId}
              AND event_type = #{eventType}
            LIMIT 1
            """)
    OutboxEvent selectByAggregateAndType(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("eventType") String eventType);

    @Select("""
            SELECT id
            FROM outbox_event
            WHERE (status = 'PENDING'
                   AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
               OR (status = 'PROCESSING' AND locked_at <= #{staleBefore})
            ORDER BY created_at, id
            LIMIT #{batchSize}
            """)
    List<Long> selectClaimableIds(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("batchSize") int batchSize);

    @Update("""
            UPDATE outbox_event
            SET status = 'PROCESSING',
                lock_owner = #{lockOwner},
                locked_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
              AND ((status = 'PENDING'
                    AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
                   OR (status = 'PROCESSING'
                       AND locked_at <= #{staleBefore}))
            """)
    int claim(
            @Param("id") Long id,
            @Param("lockOwner") String lockOwner,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE outbox_event
            SET status = 'PUBLISHED',
                published_at = #{now},
                next_retry_at = NULL,
                locked_at = NULL,
                lock_owner = NULL,
                last_error = NULL,
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND lock_owner = #{lockOwner}
            """)
    int markPublished(
            @Param("id") Long id,
            @Param("lockOwner") String lockOwner,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE outbox_event
            SET status = CASE
                    WHEN #{nextRetryCount} >= #{maxRetryCount}
                    THEN 'FAILED' ELSE 'PENDING' END,
                retry_count = #{nextRetryCount},
                next_retry_at = CASE
                    WHEN #{nextRetryCount} >= #{maxRetryCount}
                    THEN NULL ELSE #{nextRetryAt} END,
                last_error = #{lastError},
                locked_at = NULL,
                lock_owner = NULL,
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND lock_owner = #{lockOwner}
            """)
    int recordFailure(
            @Param("id") Long id,
            @Param("lockOwner") String lockOwner,
            @Param("nextRetryCount") int nextRetryCount,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE outbox_event
            SET status = 'PENDING',
                retry_count = 0,
                next_retry_at = #{now},
                locked_at = NULL,
                lock_owner = NULL,
                last_error = NULL,
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'FAILED'
            """)
    int retryFailed(
            @Param("id") Long id,
            @Param("now") LocalDateTime now);
}
