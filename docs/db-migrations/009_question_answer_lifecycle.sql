-- Question tracking phase 2: candidate answers, completion and subscriber lifecycle.

UPDATE question
SET status = 'OPEN', accepted_answer_id = NULL
WHERE status = 'CLOSED';

UPDATE question
SET status = 'COMPLETED'
WHERE status = 'ANSWERED';

CREATE TABLE IF NOT EXISTS question_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    answerer_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_question_answer_comment (question_id, comment_id),
    KEY idx_question_answer_status_created (question_id, status, created_at),
    KEY idx_question_answer_answerer_created (answerer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @question_id_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'notice'
      AND column_name = 'question_id'
);
SET @question_id_column_sql = IF(
    @question_id_column_exists = 0,
    'ALTER TABLE notice ADD COLUMN question_id BIGINT DEFAULT NULL AFTER comment_id',
    'SELECT 1'
);
PREPARE question_id_column_stmt FROM @question_id_column_sql;
EXECUTE question_id_column_stmt;
DEALLOCATE PREPARE question_id_column_stmt;

SET @question_id_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notice'
      AND index_name = 'idx_notice_question'
);
SET @question_id_index_sql = IF(
    @question_id_index_exists = 0,
    'ALTER TABLE notice ADD INDEX idx_notice_question (question_id)',
    'SELECT 1'
);
PREPARE question_id_index_stmt FROM @question_id_index_sql;
EXECUTE question_id_index_stmt;
DEALLOCATE PREPARE question_id_index_stmt;
