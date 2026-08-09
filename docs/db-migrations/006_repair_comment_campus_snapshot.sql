-- Repairs historical comment snapshots written with a double-encoded Chinese campus name.
-- It only touches values beginning with the characteristic UTF-8 bytes C3A4-C3A9.
UPDATE `comment`
SET author_campus_name = CONVERT(BINARY CONVERT(author_campus_name USING latin1) USING utf8mb4)
WHERE HEX(author_campus_name) REGEXP '^C3A[4-9]';
