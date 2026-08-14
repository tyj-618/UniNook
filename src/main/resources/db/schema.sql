SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS campuscircle
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE campuscircle;

CREATE TABLE IF NOT EXISTS admin_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT DEFAULT NULL,
    action VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_admin_action_created (admin_user_id, created_at),
    KEY idx_admin_action_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_outbox (
    id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    published_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_status_next_attempt (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='transactional outbox';

CREATE TABLE IF NOT EXISTS university (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '高校ID',
    name VARCHAR(64) NOT NULL COMMENT '高校名称',
    province VARCHAR(32) NOT NULL COMMENT '省份',
    city VARCHAR(32) NOT NULL COMMENT '城市',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-启用，1-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_university_name_city (name, city),
    KEY idx_university_province_city_status (province, city, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='高校表';

CREATE TABLE IF NOT EXISTS school (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学校ID',
    university_id BIGINT DEFAULT NULL COMMENT '所属高校ID，兼容历史学校数据',
    name VARCHAR(64) NOT NULL COMMENT '学校名称',
    campus_name VARCHAR(64) NOT NULL DEFAULT '主校区' COMMENT '校区名称',
    province VARCHAR(32) NOT NULL COMMENT '省份',
    city VARCHAR(32) NOT NULL COMMENT '城市',
    latitude DECIMAL(10, 6) NOT NULL COMMENT '纬度',
    longitude DECIMAL(10, 6) NOT NULL COMMENT '经度',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-启用，1-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_city_status (city, status),
    KEY idx_university_status (university_id, status),
    KEY idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校表';

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '加密后的密码',
    nickname VARCHAR(32) NOT NULL COMMENT '昵称',
    nickname_confirmed TINYINT NOT NULL DEFAULT 1 COMMENT '是否已确认昵称',
    school_id BIGINT DEFAULT NULL COMMENT '所属学校ID，首次注册后由用户绑定',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    bio VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
    role TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户，1-管理员',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    KEY idx_school_id (school_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(32) NOT NULL COMMENT '分类名称',
    code VARCHAR(32) NOT NULL COMMENT '分类编码',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-启用，1-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子分类表';

CREATE TABLE IF NOT EXISTS post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '发帖用户ID',
    school_id BIGINT NOT NULL COMMENT '帖子所属学校ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '正文内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除，2-隐藏',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_id (user_id),
    KEY idx_school_created (school_id, created_at),
    KEY idx_category_created (category_id, created_at),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子表';

CREATE TABLE IF NOT EXISTS post_stat (
    post_id BIGINT PRIMARY KEY COMMENT '帖子ID',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览数',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数',
    hot_score DOUBLE NOT NULL DEFAULT 0 COMMENT '热度分',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_hot_score (hot_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子统计表';

CREATE TABLE IF NOT EXISTS `comment` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '评论用户ID',
    author_school_id BIGINT DEFAULT NULL COMMENT '评论发表时的学校ID快照',
    author_school_name VARCHAR(64) DEFAULT NULL COMMENT '评论发表时的学校名称快照',
    author_campus_name VARCHAR(64) DEFAULT NULL COMMENT '评论发表时的校区名称快照',
    root_comment_id BIGINT DEFAULT NULL COMMENT '顶级评论ID，顶级评论为空',
    parent_comment_id BIGINT DEFAULT NULL COMMENT '直接回复的评论ID',
    reply_to_user_id BIGINT DEFAULT NULL COMMENT '被回复用户ID',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    content VARCHAR(500) NOT NULL COMMENT '评论内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_post_created (post_id, created_at),
    KEY idx_post_root_created (post_id, root_comment_id, created_at),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

CREATE TABLE IF NOT EXISTS comment_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

CREATE TABLE IF NOT EXISTS school_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    from_school_id BIGINT NOT NULL COMMENT '修改前学校ID',
    to_school_id BIGINT NOT NULL COMMENT '修改后学校ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校修改记录';

CREATE TABLE IF NOT EXISTS post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点赞ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-已点赞，1-已取消',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_post_user (post_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

CREATE TABLE IF NOT EXISTS question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问题追踪ID',
    source_type VARCHAR(16) NOT NULL COMMENT '来源类型：POST/COMMENT',
    source_id BIGINT NOT NULL COMMENT '来源帖子或评论ID',
    asker_id BIGINT NOT NULL COMMENT '问题发起用户ID',
    question_text VARCHAR(300) NOT NULL COMMENT '希望获得的结果',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/COMPLETED',
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

CREATE TABLE IF NOT EXISTS question_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选答复ID',
    question_id BIGINT NOT NULL COMMENT '问题追踪ID',
    comment_id BIGINT NOT NULL COMMENT '关联评论ID',
    answerer_id BIGINT NOT NULL COMMENT '答复者ID',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED/WITHDRAWN',
    reviewed_by BIGINT DEFAULT NULL COMMENT '审核者ID',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_answer_comment (question_id, comment_id),
    KEY idx_question_answer_status_created (question_id, status, created_at),
    KEY idx_question_answer_answerer_created (answerer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题候选答复表';

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
    receiver_id BIGINT NOT NULL COMMENT '接收通知用户ID',
    sender_id BIGINT NOT NULL COMMENT '触发通知用户ID',
    post_id BIGINT DEFAULT NULL COMMENT '关联帖子ID',
    comment_id BIGINT DEFAULT NULL COMMENT '关联评论ID',
    question_id BIGINT DEFAULT NULL COMMENT '关联问题追踪ID',
    type TINYINT NOT NULL COMMENT '通知类型：1-评论，2-点赞',
    event_key VARCHAR(128) NOT NULL COMMENT '业务事件幂等键',
    content VARCHAR(255) NOT NULL COMMENT '通知内容',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态：0-未读，1-已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_event_key (event_key),
    KEY idx_receiver_read_created (receiver_id, read_status, created_at),
    KEY idx_receiver_created (receiver_id, created_at)
    ,KEY idx_notice_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';
