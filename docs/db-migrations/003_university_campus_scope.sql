-- UniNook 高校-校区模型升级（MySQL 8）
-- 适用于已经运行过早期 school_id 版本的本地或测试数据库。
-- 执行前请先备份数据库；新建数据库无需单独执行，本变更已包含在 schema.sql 中。

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

ALTER TABLE school
    ADD COLUMN university_id BIGINT DEFAULT NULL COMMENT '所属高校ID，兼容历史学校数据' AFTER id,
    ADD COLUMN campus_name VARCHAR(64) NOT NULL DEFAULT '主校区' COMMENT '校区名称' AFTER name,
    ADD KEY idx_university_status (university_id, status);

ALTER TABLE `comment`
    ADD COLUMN author_campus_name VARCHAR(64) DEFAULT NULL COMMENT '评论发表时的校区名称快照' AFTER author_school_name;

INSERT INTO university (name, province, city, status)
SELECT s.name, s.province, s.city, s.status
FROM school s
LEFT JOIN university u ON u.name = s.name AND u.city = s.city
WHERE u.id IS NULL
GROUP BY s.name, s.province, s.city, s.status;

UPDATE school s
JOIN university u ON u.name = s.name AND u.city = s.city
SET s.university_id = u.id
WHERE s.university_id IS NULL;

UPDATE `comment` c
JOIN school s ON c.author_school_id = s.id
SET c.author_campus_name = s.campus_name
WHERE c.author_campus_name IS NULL;
