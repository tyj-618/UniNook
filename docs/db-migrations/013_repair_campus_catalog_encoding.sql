-- Repair catalog rows written with an incorrect client charset during the
-- 012 migration rollout. This migration never deletes data: malformed catalog
-- rows are disabled, canonical rows are restored or inserted.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
START TRANSACTION;

-- Restore the four built-in university records without relying on the shell
-- or editor encoding. The hexadecimal literals below are UTF-8 text.
UPDATE university
SET name = CONVERT(0xE58D97E4BAACE5A4A7E5ADA6 USING utf8mb4),
    province = CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
    city = CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4),
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE university
SET name = CONVERT(0xE4B89CE58D97E5A4A7E5ADA6 USING utf8mb4),
    province = CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
    city = CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4),
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2;

UPDATE university
SET name = CONVERT(0xE58D97E4BAACE888AAE7A9BAE888AAE5A4A9E5A4A7E5ADA6 USING utf8mb4),
    province = CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
    city = CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4),
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 3;

UPDATE university
SET name = CONVERT(0xE5A48DE697A6E5A4A7E5ADA6 USING utf8mb4),
    province = CONVERT(0xE4B88AE6B5B7E5B882 USING utf8mb4),
    city = CONVERT(0xE4B88AE6B5B7E5B882 USING utf8mb4),
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 4;

-- Mojibake produced from UTF-8 bytes interpreted as Latin-1 contains UTF-8
-- encodings of C1 control characters (C2 80 through C2 9F). Valid catalog
-- labels never contain that byte sequence, so disable only malformed rows and
-- keep them for auditability.
UPDATE school
SET status = 1
WHERE status = 0
  AND (
      HEX(name) REGEXP 'C2(8[0-9A-F]|9[A-F])'
      OR HEX(campus_name) REGEXP 'C2(8[0-9A-F]|9[A-F])'
      OR HEX(province) REGEXP 'C2(8[0-9A-F]|9[A-F])'
      OR HEX(city) REGEXP 'C2(8[0-9A-F]|9[A-F])'
  );

-- Link legacy valid rows after the university labels are restored.
UPDATE school s
JOIN university u ON u.name COLLATE utf8mb4_unicode_ci = s.name
               AND u.city COLLATE utf8mb4_unicode_ci = s.city
SET s.university_id = u.id
WHERE s.university_id IS NULL;

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE58D97E4BAACE5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE4BB99E69E97E6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
       CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4), 32.115055, 118.958743, 0
FROM university u
WHERE u.id = 1 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE4BB99E69E97E6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE58D97E4BAACE5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE9BC93E6A5BCE6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
       CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4), 32.056691, 118.784265, 0
FROM university u
WHERE u.id = 1 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE9BC93E6A5BCE6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE4B89CE58D97E5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE4B99DE9BE99E6B996E6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
       CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4), 31.887891, 118.818982, 0
FROM university u
WHERE u.id = 2 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE4B99DE9BE99E6B996E6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE4B89CE58D97E5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE59B9BE7898CE6A5BCE6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
       CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4), 32.055119, 118.796648, 0
FROM university u
WHERE u.id = 2 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE59B9BE7898CE6A5BCE6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE58D97E4BAACE888AAE7A9BAE888AAE5A4A9E5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE5B086E5869BE8B7AFE6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE6B19FE88B8FE79C81 USING utf8mb4),
       CONVERT(0xE58D97E4BAACE5B882 USING utf8mb4), 32.034454, 118.797028, 0
FROM university u
WHERE u.id = 3 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE5B086E5869BE8B7AFE6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, CONVERT(0xE5A48DE697A6E5A4A7E5ADA6 USING utf8mb4),
       CONVERT(0xE982AFE983B8E6A0A1E58CBA USING utf8mb4),
       CONVERT(0xE4B88AE6B5B7E5B882 USING utf8mb4),
       CONVERT(0xE4B88AE6B5B7E5B882 USING utf8mb4), 31.298822, 121.503223, 0
FROM university u
WHERE u.id = 4 AND NOT EXISTS (
    SELECT 1 FROM school s WHERE s.university_id = u.id
    AND s.campus_name = (CONVERT(0xE982AFE983B8E6A0A1E58CBA USING utf8mb4) COLLATE utf8mb4_unicode_ci) AND s.status = 0
);

COMMIT;
