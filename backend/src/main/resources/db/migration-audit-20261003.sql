CREATE TABLE IF NOT EXISTS user_contest (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    contest_id BIGINT NOT NULL,
    external_handle VARCHAR(64) DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'JOINED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_uc_user_contest (user_id, contest_id),
    KEY idx_uc_contest (contest_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS friendly_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    url VARCHAR(500) NOT NULL,
    logo VARCHAR(500) DEFAULT NULL,
    description VARCHAR(200) DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'cf_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `cf_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'atcoder_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `atcoder_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'luogu_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `luogu_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'nowcoder_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `nowcoder_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'leetcode_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `leetcode_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'qoj_handle') = 0, 'ALTER TABLE `user` ADD COLUMN `qoj_handle` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'show_code') = 0, 'ALTER TABLE `user` ADD COLUMN `show_code` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否公开提交代码（仅 PUBLIC 档有效）''', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_leetcode_handle') = 0, 'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_leetcode_handle` (`leetcode_handle`)', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_qoj_handle') = 0, 'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_qoj_handle` (`qoj_handle`)', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'qq') = 0, 'ALTER TABLE `user` ADD COLUMN `qq` VARCHAR(32) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'wechat') = 0, 'ALTER TABLE `user` ADD COLUMN `wechat` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone') = 0, 'ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest' AND COLUMN_NAME = 'external_id') = 0, 'ALTER TABLE `contest` ADD COLUMN `external_id` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'participant_type') = 0, 'ALTER TABLE `contest_log` ADD COLUMN `participant_type` VARCHAR(16) NOT NULL DEFAULT ''TEAM''', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'user_id') = 0, 'ALTER TABLE `contest_log` ADD COLUMN `user_id` BIGINT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'ac_count') = 0, 'ALTER TABLE `contest_log` ADD COLUMN `ac_count` INT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'duration_seconds') = 0, 'ALTER TABLE `contest_log` ADD COLUMN `duration_seconds` INT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'penalty_minutes') = 0, 'ALTER TABLE `contest_log` ADD COLUMN `penalty_minutes` INT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar') IN ('varchar', 'tinytext', 'text'), 'ALTER TABLE `user` MODIFY COLUMN `avatar` MEDIUMTEXT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND COLUMN_NAME = 'team_id') = 'NO', 'ALTER TABLE `contest_log` MODIFY COLUMN `team_id` BIGINT DEFAULT NULL', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submission_record' AND INDEX_NAME = 'uk_sub_user_problem_time') = 0, 'ALTER TABLE `submission_record` ADD UNIQUE KEY `uk_sub_user_problem_time` (`user_id`, `problem_id`, `submitted_at`)', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contest_log' AND INDEX_NAME = 'idx_cl_participant') = 0, 'ALTER TABLE `contest_log` ADD KEY `idx_cl_participant` (`participant_type`, `user_id`)', 'SELECT 1');
PREPARE audit_stmt FROM @audit_sql;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL DEFAULT '',
    target_type VARCHAR(32) DEFAULT NULL,
    target_id BIGINT DEFAULT NULL,
    read_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_user_time (user_id, created_at, id),
    KEY idx_notification_user_unread (user_id, read_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS article_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_attachment_article (article_id, id),
    CONSTRAINT fk_article_attachment_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS award_certificate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    award_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_award_certificate_award (award_id),
    CONSTRAINT fk_award_certificate_award FOREIGN KEY (award_id) REFERENCES award (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
