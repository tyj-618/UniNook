-- Restore the default university-campus catalog for databases created before
-- multi-campus records were added. This migration is idempotent and does not
-- remove or overwrite user, post, comment, or existing campus records.

-- Keep the connection charset explicit when this file is piped to mysql.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO university (id, name, province, city, status)
VALUES
    (1, '南京大学', '江苏省', '南京市', 0),
    (2, '东南大学', '江苏省', '南京市', 0),
    (3, '南京航空航天大学', '江苏省', '南京市', 0),
    (4, '复旦大学', '上海市', '上海市', 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    province = VALUES(province),
    city = VALUES(city),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP;

-- Ensure schools created by the pre-university schema are linked before
-- checking whether a campus already exists.
UPDATE school s
JOIN university u ON u.name COLLATE utf8mb4_unicode_ci = s.name
               AND u.city COLLATE utf8mb4_unicode_ci = s.city
SET s.university_id = u.id
WHERE s.university_id IS NULL;

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '南京大学', '仙林校区', '江苏省', '南京市', 32.115055, 118.958743, 0
FROM university u
WHERE u.name = '南京大学' AND u.city = '南京市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '仙林校区'
  );

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '南京大学', '鼓楼校区', '江苏省', '南京市', 32.056691, 118.784265, 0
FROM university u
WHERE u.name = '南京大学' AND u.city = '南京市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '鼓楼校区'
  );

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '东南大学', '九龙湖校区', '江苏省', '南京市', 31.887891, 118.818982, 0
FROM university u
WHERE u.name = '东南大学' AND u.city = '南京市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '九龙湖校区'
  );

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '东南大学', '四牌楼校区', '江苏省', '南京市', 32.055119, 118.796648, 0
FROM university u
WHERE u.name = '东南大学' AND u.city = '南京市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '四牌楼校区'
  );

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '南京航空航天大学', '将军路校区', '江苏省', '南京市', 32.034454, 118.797028, 0
FROM university u
WHERE u.name = '南京航空航天大学' AND u.city = '南京市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '将军路校区'
  );

INSERT INTO school (university_id, name, campus_name, province, city, latitude, longitude, status)
SELECT u.id, '复旦大学', '邯郸校区', '上海市', '上海市', 31.298822, 121.503223, 0
FROM university u
WHERE u.name = '复旦大学' AND u.city = '上海市'
  AND NOT EXISTS (
      SELECT 1 FROM school s WHERE s.university_id = u.id AND s.campus_name = '邯郸校区'
  );
