-- Phase 1: Question and subscription foundation.
-- This migration is additive. It intentionally preserves post_follow/post_progress for rollback and comparison.

CREATE TABLE IF NOT EXISTS question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问题追踪ID',
    source_type VARCHAR(16) NOT NULL COMMENT '来源类型：POST/COMMENT',
    source_id BIGINT NOT NULL COMMENT '来源帖子或评论ID',
    asker_id BIGINT NOT NULL COMMENT '问题发起用户ID',
    question_text VARCHAR(300) NOT NULL COMMENT '希望获得的结果',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/ANSWERED/CLOSED',
    accepted_answer_id BIGINT DEFAULT NULL COMMENT '已采纳答复ID，阶段2启用',
    subscriber_count INT NOT NULL DEFAULT 0 COMMENT '订阅人数',
    last_answer_at DATETIME DEFAULT NULL COMMENT '最后答复时间，阶段2启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_source (source_type, source_id),
    KEY idx_question_asker_updated (asker_id, updated_at),
    KEY idx_question_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题追踪表';

CREATE TABLE IF NOT EXISTS question_subscription (
    question_id BIGINT NOT NULL COMMENT '问题追踪ID',
    user_id BIGINT NOT NULL COMMENT '订阅用户ID',
    last_read_answer_id BIGINT DEFAULT NULL COMMENT '最后已读答复ID，阶段2启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    PRIMARY KEY (question_id, user_id),
    KEY idx_question_subscription_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题追踪订阅表';
