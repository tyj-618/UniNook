CREATE TABLE IF NOT EXISTS report (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
