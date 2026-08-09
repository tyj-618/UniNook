-- Restore the MVP invariant: one question tracking record per post or comment.
-- Preflight first. Resolve every returned duplicate manually before running the ALTER.
SELECT source_type, source_id, COUNT(*) AS question_count
FROM question
GROUP BY source_type, source_id
HAVING COUNT(*) > 1;

ALTER TABLE question DROP INDEX idx_question_source_created;
ALTER TABLE question ADD UNIQUE KEY uk_question_source (source_type, source_id);
