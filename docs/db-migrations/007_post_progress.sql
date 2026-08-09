ALTER TABLE post
    ADD COLUMN progress_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '进展状态：OPEN/UPDATED/RESOLVED' AFTER status;

CREATE TABLE IF NOT EXISTS post_follow (
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '关注用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (post_id, user_id),
    KEY idx_post_follow_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子进展关注表';

CREATE TABLE IF NOT EXISTS post_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '进展ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    author_id BIGINT NOT NULL COMMENT '发布进展用户ID',
    type VARCHAR(16) NOT NULL COMMENT '进展类型：UPDATE/RESOLVED',
    content VARCHAR(500) NOT NULL COMMENT '进展内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    KEY idx_post_progress_created (post_id, created_at),
    KEY idx_post_progress_author_created (author_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子进展表';
