-- Permit several independent questions on the same post or comment.
-- Application limits: POST <= 5, COMMENT <= 2.
ALTER TABLE question DROP INDEX uk_question_source;
ALTER TABLE question ADD KEY idx_question_source_created (source_type, source_id, created_at);
