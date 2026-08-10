-- Apply once to an existing UniNook database.
ALTER TABLE `comment`
    ADD COLUMN author_school_id BIGINT NULL DEFAULT NULL COMMENT '评论发表时的学校ID快照' AFTER user_id,
    ADD COLUMN author_school_name VARCHAR(64) NULL DEFAULT NULL COMMENT '评论发表时的学校名称快照' AFTER author_school_id;

UPDATE `comment` c
JOIN `user` u ON u.id = c.user_id
LEFT JOIN school s ON s.id = u.school_id
SET c.author_school_id = u.school_id,
    c.author_school_name = s.name
WHERE c.author_school_id IS NULL;

CREATE TABLE IF NOT EXISTS school_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    from_school_id BIGINT NOT NULL COMMENT '修改前学校ID',
    to_school_id BIGINT NOT NULL COMMENT '修改后学校ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校修改记录';
