-- Apply once to an existing UniNook database before deploying comment replies.
ALTER TABLE `comment`
    ADD COLUMN root_comment_id BIGINT NULL DEFAULT NULL COMMENT '顶级评论ID，顶级评论为空' AFTER user_id,
    ADD COLUMN parent_comment_id BIGINT NULL DEFAULT NULL COMMENT '直接回复的评论ID' AFTER root_comment_id,
    ADD COLUMN reply_to_user_id BIGINT NULL DEFAULT NULL COMMENT '被回复用户ID' AFTER parent_comment_id,
    ADD KEY idx_post_root_created (post_id, root_comment_id, created_at);
