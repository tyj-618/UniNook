DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS admin_action_log;
DROP TABLE IF EXISTS question_answer;
DROP TABLE IF EXISTS question_subscription;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS school_change_log;
DROP TABLE IF EXISTS comment_like;
DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS post_stat;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS school;
DROP TABLE IF EXISTS university;

CREATE TABLE university (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    province VARCHAR(32) NOT NULL,
    city VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_university_name_city (name, city),
    KEY idx_university_province_city_status (province, city, status)
);

CREATE TABLE school (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    university_id BIGINT DEFAULT NULL,
    name VARCHAR(64) NOT NULL,
    campus_name VARCHAR(64) NOT NULL DEFAULT '主校区',
    province VARCHAR(32) NOT NULL,
    city VARCHAR(32) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    longitude DECIMAL(10, 6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_city_status (city, status),
    KEY idx_university_status (university_id, status),
    KEY idx_location (latitude, longitude)
);

CREATE TABLE `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    nickname_confirmed TINYINT NOT NULL DEFAULT 1,
    school_id BIGINT DEFAULT NULL,
    avatar_url VARCHAR(255) DEFAULT NULL,
    bio VARCHAR(255) DEFAULT NULL,
    role TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    KEY idx_user_school_id (school_id)
);

CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    code VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
);

CREATE TABLE post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_post_user_id (user_id),
    KEY idx_school_created (school_id, created_at),
    KEY idx_category_created (category_id, created_at),
    KEY idx_created_at (created_at)
);

CREATE TABLE post_stat (
    post_id BIGINT PRIMARY KEY,
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    hot_score DOUBLE NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hot_score (hot_score)
);

CREATE TABLE `comment` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    author_school_id BIGINT DEFAULT NULL,
    author_school_name VARCHAR(64) DEFAULT NULL,
    author_campus_name VARCHAR(64) DEFAULT NULL,
    root_comment_id BIGINT DEFAULT NULL,
    parent_comment_id BIGINT DEFAULT NULL,
    reply_to_user_id BIGINT DEFAULT NULL,
    like_count INT NOT NULL DEFAULT 0,
    content VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_post_created (post_id, created_at),
    KEY idx_post_root_created (post_id, root_comment_id, created_at),
    KEY idx_comment_user_id (user_id)
);

CREATE TABLE comment_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    KEY idx_user_id (user_id)
);

CREATE TABLE school_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    from_school_id BIGINT NOT NULL,
    to_school_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_created (user_id, created_at)
);

CREATE TABLE post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    KEY idx_post_like_user_id (user_id)
);

CREATE TABLE question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT NOT NULL,
    asker_id BIGINT NOT NULL,
    question_text VARCHAR(300) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    accepted_answer_id BIGINT DEFAULT NULL,
    subscriber_count INT NOT NULL DEFAULT 0,
    last_answer_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_question_source (source_type, source_id),
    KEY idx_question_asker_updated (asker_id, updated_at),
    KEY idx_question_status_updated (status, updated_at)
);

CREATE TABLE question_subscription (
    question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_answer_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (question_id, user_id),
    KEY idx_question_subscription_user_created (user_id, created_at)
);

CREATE TABLE question_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    answerer_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_question_answer_comment (question_id, comment_id),
    KEY idx_question_answer_status_created (question_id, status, created_at)
);

CREATE TABLE notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    post_id BIGINT DEFAULT NULL,
    comment_id BIGINT DEFAULT NULL,
    question_id BIGINT DEFAULT NULL,
    type TINYINT NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    content VARCHAR(255) NOT NULL,
    read_status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_key (event_key),
    KEY idx_receiver_read_created (receiver_id, read_status, created_at),
    KEY idx_receiver_created (receiver_id, created_at)
    ,KEY idx_notice_question (question_id)
);

CREATE TABLE admin_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT DEFAULT NULL,
    detail VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_admin_action_created (admin_user_id, created_at),
    KEY idx_target_created (target_type, target_id, created_at)
);

CREATE TABLE report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    admin_id BIGINT DEFAULT NULL,
    admin_note VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME DEFAULT NULL,
    KEY idx_report_status_created (status, created_at),
    KEY idx_report_target (target_type, target_id),
    KEY idx_report_reporter_created (reporter_id, created_at)
);

CREATE TABLE feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    rating VARCHAR(16) NOT NULL,
    comment VARCHAR(500) DEFAULT NULL,
    question_text VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feedback_user_request (user_id, request_id),
    KEY idx_feedback_request_rating (request_id, rating),
    KEY idx_feedback_created (created_at)
);
