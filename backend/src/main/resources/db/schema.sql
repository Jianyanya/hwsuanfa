-- ============================================================
-- 校队算法训练平台 —— 建表脚本
-- MySQL 8.0 / utf8mb4
--
-- 设计要点（与项目计划书第 6 章对应）：
--   1. 积分 = 事件流水(score_event) + 汇总值(user_score_summary)，事件不可覆盖
--   2. 幂等：score_event.idempotent_key 唯一索引，防重复发放
--   3. 一队一队长：team.leader_user_id + 业务锁双重保证
--   4. 全体作业：assignment_target 保存发布时的队伍快照
--   5. 隐私：privacy_level 存 user 表，判断在后端做
-- ============================================================

CREATE DATABASE IF NOT EXISTS `hwsuanfa`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `hwsuanfa`;

-- ------------------------------------------------------------
-- 1. user —— 用户
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    -- ⚠️ username 存的是**学号**：登录账号 = 学号。
    --    这样登录链路（JWT / LoginUserService）不用改动，学号也天然唯一。
    `username`        VARCHAR(32)  NOT NULL                COMMENT '登录账号 = 学号，唯一',
    `password`        VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希，绝不返回给前端',
    `nickname`        VARCHAR(32)  NOT NULL                COMMENT '名称 / 站内显示名，唯一（防冒用）',
    `real_name`       VARCHAR(32)           DEFAULT NULL   COMMENT '真实姓名（仅站主可见）',
    `student_no`      VARCHAR(32)           DEFAULT NULL   COMMENT '学号，唯一（仅站主可见）',
    `class_name`      VARCHAR(64)           DEFAULT NULL   COMMENT '班级',
    `avatar`          MEDIUMTEXT            DEFAULT NULL,
    `email`           VARCHAR(64)           DEFAULT NULL,
    `qq`              VARCHAR(32)           DEFAULT NULL,
    `wechat`          VARCHAR(64)           DEFAULT NULL,
    `phone`           VARCHAR(20)           DEFAULT NULL,
    -- 角色：SITE_OWNER 由管理员邀请码注册产生（等同站主，同一人）
    `role`            VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT 'SITE_OWNER / MEMBER',
    `privacy_level`   VARCHAR(16)  NOT NULL DEFAULT 'PARTIAL' COMMENT 'PRIVATE / PARTIAL / PUBLIC',
    `show_code`       TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否公开提交代码（仅 PUBLIC 档有效）',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `oj_accounts`     JSON                  DEFAULT NULL   COMMENT '各平台账号 {"CODEFORCES":"xxx"}',
    `cf_handle`       VARCHAR(64)           DEFAULT NULL,
    `atcoder_handle`  VARCHAR(64)           DEFAULT NULL,
    `luogu_handle`    VARCHAR(64)           DEFAULT NULL,
    `leetcode_handle` VARCHAR(64)           DEFAULT NULL,
    `nowcoder_handle` VARCHAR(64)           DEFAULT NULL,
    `qoj_handle`      VARCHAR(64)           DEFAULT NULL,
    `last_login_at`   DATETIME              DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    -- 名称与学号都唯一：名称防冒用，学号是登录凭据
    -- 注意 MySQL 唯一索引中 NULL 不参与比较，所以历史行的 NULL 不会互相冲突
    UNIQUE KEY `uk_user_nickname` (`nickname`),
    UNIQUE KEY `uk_user_student_no` (`student_no`),
    UNIQUE KEY `uk_user_cf_handle` (`cf_handle`),
    UNIQUE KEY `uk_user_atcoder_handle` (`atcoder_handle`),
    UNIQUE KEY `uk_user_luogu_handle` (`luogu_handle`),
    UNIQUE KEY `uk_user_nowcoder_handle` (`nowcoder_handle`),
    UNIQUE KEY `uk_user_leetcode_handle` (`leetcode_handle`),
    UNIQUE KEY `uk_user_qoj_handle` (`qoj_handle`),
    KEY `idx_user_role` (`role`),
    KEY `idx_user_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- ------------------------------------------------------------
-- 2. team —— 队伍
--    一个队伍必须有且仅有一名队长，且队长必须是本队成员
--    （leader_user_id 指向 user，成员关系由 team_member 保证）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `team`;
CREATE TABLE `team` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(64)  NOT NULL,
    `description`    VARCHAR(500)          DEFAULT NULL,
    `leader_user_id` BIGINT                DEFAULT NULL   COMMENT '当前队长，唯一（一队一队长）',
    -- 上限已按用户要求固定为 3，存字段是为了以后可调，业务校验读它
    `max_members`    TINYINT      NOT NULL DEFAULT 3       COMMENT '成员上限，当前固定 3',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / FROZEN',
    `frozen_reason`  VARCHAR(255)          DEFAULT NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_name` (`name`),
    KEY `idx_team_leader` (`leader_user_id`),
    KEY `idx_team_status` (`status`),
    CONSTRAINT `fk_team_leader` FOREIGN KEY (`leader_user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '队伍';

-- ------------------------------------------------------------
-- 3. team_member —— 队伍成员关系
--    同一用户在同一队伍只能有一条 ACTIVE 记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `team_member`;
CREATE TABLE `team_member` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `team_id`    BIGINT      NOT NULL,
    `user_id`    BIGINT      NOT NULL,
    `status`     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / LEFT',
    `joined_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `left_at`    DATETIME             DEFAULT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 一个用户在同一队伍只能有一条记录（退出再进复用同一条，改 status）
    UNIQUE KEY `uk_team_member` (`team_id`, `user_id`),
    KEY `idx_tm_user` (`user_id`, `status`),
    KEY `idx_tm_team` (`team_id`, `status`),
    CONSTRAINT `fk_tm_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_tm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '队伍成员关系';

-- ------------------------------------------------------------
-- 4. problem —— 题目（无独立题库入口，仅作作业/题单/比赛/提交的基础数据）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(200) NOT NULL,
    `platform`    VARCHAR(16)  NOT NULL                COMMENT 'CODEFORCES/ATCODER/NOWCODER/LEETCODE/LUOGU/OTHER',
    `problem_no`  VARCHAR(64)           DEFAULT NULL   COMMENT '平台内题号，如 1932F',
    `url`         VARCHAR(500)          DEFAULT NULL,
    `difficulty`  VARCHAR(32)           DEFAULT NULL   COMMENT '难度标签（各平台口径不同，原样存）',
    `tags`        VARCHAR(255)          DEFAULT NULL   COMMENT '逗号分隔',
    `note`        VARCHAR(500)          DEFAULT NULL,
    -- 平台 + 题号唯一，防止同一道题被重复录入
    `created_by`  BIGINT                DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_platform_no` (`platform`, `problem_no`),
    KEY `idx_problem_created_by` (`created_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目';

-- ------------------------------------------------------------
-- 5. problem_list —— 题单（个人 / 队伍）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `problem_list`;
CREATE TABLE `problem_list` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(128) NOT NULL,
    `description`  VARCHAR(500)          DEFAULT NULL,
    `scope`        VARCHAR(16)  NOT NULL                COMMENT 'PERSONAL / TEAM',
    `team_id`      BIGINT                DEFAULT NULL   COMMENT 'scope=TEAM 时必填',
    `owner_id`     BIGINT       NOT NULL                COMMENT '创建人',
    -- 审核：队员提交的题单先进 PENDING，队长或站主确认后转 APPROVED
    `audit_status` VARCHAR(16)  NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING / APPROVED / REJECTED',
    `audited_by`   BIGINT                DEFAULT NULL,
    `audited_at`   DATETIME              DEFAULT NULL,
    `reject_reason` VARCHAR(255)         DEFAULT NULL,
    `last_editor_id` BIGINT              DEFAULT NULL   COMMENT '最后维护人',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pl_team` (`team_id`, `audit_status`),
    KEY `idx_pl_owner` (`owner_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题单';

-- ------------------------------------------------------------
-- 6. problem_list_item —— 题单内容（记录顺序）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `problem_list_item`;
CREATE TABLE `problem_list_item` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT,
    `problem_list_id` BIGINT   NOT NULL,
    `problem_id`      BIGINT   NOT NULL,
    `sort_order`      INT      NOT NULL DEFAULT 0       COMMENT '题单内顺序',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 同一题单里同一题只能出现一次
    UNIQUE KEY `uk_pli_list_problem` (`problem_list_id`, `problem_id`),
    KEY `idx_pli_list_sort` (`problem_list_id`, `sort_order`),
    CONSTRAINT `fk_pli_list` FOREIGN KEY (`problem_list_id`) REFERENCES `problem_list` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pli_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题单内容';

-- ------------------------------------------------------------
-- 7. assignment —— 作业 / 每日一题（同一张表，用 type 区分）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `assignment`;
CREATE TABLE `assignment` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `type`          VARCHAR(16)  NOT NULL                COMMENT 'TEAM / GLOBAL / DAILY',
    `title`         VARCHAR(200) NOT NULL,
    `description`   TEXT                  DEFAULT NULL,
    `problem_list_id` BIGINT              DEFAULT NULL   COMMENT '关联题单（可选）',
    `problem_id`    BIGINT                DEFAULT NULL   COMMENT '每日一题关联的单题',
    -- 时间：状态由后端按服务器时间算，不存 status
    `start_at`      DATETIME     NOT NULL,
    `due_at`        DATETIME     NOT NULL,
    `score_reward`  DECIMAL(8,2) NOT NULL DEFAULT 0      COMMENT '完成奖励积分',
    -- 逾期完成是否给分（每日一题场景由站主配置）
    `allow_late_reward` TINYINT(1) NOT NULL DEFAULT 1,
    `publish_date`  DATE                  DEFAULT NULL   COMMENT '每日一题专用：发布日，同日唯一',
    `created_by`    BIGINT       NOT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 每日一题同一天只能有一条（NULL 不参与唯一约束，所以 TEAM/GLOBAL 不受影响）
    UNIQUE KEY `uk_assignment_daily_date` (`type`, `publish_date`),
    KEY `idx_assignment_type_due` (`type`, `due_at`),
    KEY `idx_assignment_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业 / 每日一题';

-- ------------------------------------------------------------
-- 8. assignment_target —— 作业目标队伍【队伍快照】
--    全体作业发布时把当时所有有效队伍写进来；
--    以后新增队伍不会影响历史作业范围。这是硬要求。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `assignment_target`;
CREATE TABLE `assignment_target` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT   NOT NULL,
    `team_id`       BIGINT   NOT NULL,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_at_assignment_team` (`assignment_id`, `team_id`),
    KEY `idx_at_team` (`team_id`),
    CONSTRAINT `fk_at_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_at_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业目标队伍（发布时快照）';

-- ------------------------------------------------------------
-- 8.5 assignment_problem —— 作业包含的题目
--     这是「作业包含哪些题」的**唯一数据源**。
--     assignment.problem_id 只用于每日一题的便捷记录，逻辑上一律读这张表，
--     避免出现「关联题单」与「直接关联题目」两个真相来源。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `assignment_problem`;
CREATE TABLE `assignment_problem` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT   NOT NULL,
    `problem_id`    BIGINT   NOT NULL,
    `sort_order`    INT      NOT NULL DEFAULT 0,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_asgprob_pair` (`assignment_id`, `problem_id`),
    KEY `idx_asgprob_order` (`assignment_id`, `sort_order`),
    -- 约束名全库唯一，不能和 assignment_progress 的 fk_ap_* 重名（踩过 ERROR 1826）
    CONSTRAINT `fk_asgprob_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_asgprob_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业包含的题目';

-- ------------------------------------------------------------
-- 9. assignment_progress —— 完成进度（用户级）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `assignment_progress`;
CREATE TABLE `assignment_progress` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT      NOT NULL,
    `user_id`       BIGINT      NOT NULL,
    `team_id`       BIGINT               DEFAULT NULL   COMMENT '完成时所属队伍，便于按队统计',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / COMPLETED',
    -- 是否逾期完成：完成时刻 vs due_at 判定后落库，避免每次重算
    `late`          TINYINT(1)  NOT NULL DEFAULT 0,
    `completed_count` INT       NOT NULL DEFAULT 0       COMMENT '已完成题数',
    `total_count`   INT         NOT NULL DEFAULT 0       COMMENT '应完成题数',
    `completed_at`  DATETIME             DEFAULT NULL,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 一个用户在一项作业里只有一条进度
    UNIQUE KEY `uk_ap_assignment_user` (`assignment_id`, `user_id`),
    KEY `idx_ap_user` (`user_id`),
    KEY `idx_ap_team` (`team_id`),
    CONSTRAINT `fk_ap_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ap_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业完成进度';

-- ------------------------------------------------------------
-- 10. submission_record —— 做题 / 提交记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `submission_record`;
CREATE TABLE `submission_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT       NOT NULL,
    `problem_id`     BIGINT       NOT NULL,
    `platform`       VARCHAR(16)  NOT NULL,
    `problem_no`     VARCHAR(64)           DEFAULT NULL,
    -- 链接与代码都存；代码是否返回给访问者由隐私规则决定
    `submission_url` VARCHAR(500)          DEFAULT NULL   COMMENT '外部提交链接',
    `code_content`   TEXT                  DEFAULT NULL   COMMENT '代码内容（可空）',
    `code_language`  VARCHAR(32)           DEFAULT NULL,
    `result`         VARCHAR(16)  NOT NULL DEFAULT 'AC'  COMMENT 'AC / WA / TLE / MLE / RE / CE / OTHER',
    -- 首次有效完成标记：同 user+problem 只允许一条 1，用于触发首次完成积分
    `is_first_ac`    TINYINT(1)   NOT NULL DEFAULT 0,
    `submitted_at`   DATETIME     NOT NULL,
    -- 来源：手动登记 / 平台同步。洛谷只能 MANUAL，其余平台 AUTO
    `source`         VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AUTO',
    `sync_batch_id`  VARCHAR(64)           DEFAULT NULL,
    `verified_by`    BIGINT                DEFAULT NULL   COMMENT '审核人（如需审核）',
    -- 仅「首次 AC」的行有值，其余为 NULL。
    -- MySQL 唯一索引里 NULL 不参与比较，所以同一个 (user, problem)
    -- 可以有多条非首次提交，但首次 AC 只能有一条。
    `first_ac_key`   VARCHAR(64) GENERATED ALWAYS AS
                     (IF(`is_first_ac` = 1, CONCAT(`user_id`, ':', `problem_id`), NULL)) STORED
                                           COMMENT '首次AC唯一键（生成列）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- ⚠️ 不要直接对 (user_id, problem_id, is_first_ac) 建唯一索引：
    --    那样每个用户对同一道题最多只能有两行（一条 0 一条 1），
    --    重复 WA 就会违反约束报错。用生成列后只有首次 AC 参与唯一性。
    UNIQUE KEY `uk_sub_first_ac` (`first_ac_key`),
    UNIQUE KEY `uk_sub_user_problem_time` (`user_id`, `problem_id`, `submitted_at`),
    KEY `idx_sub_user_time` (`user_id`, `submitted_at`),
    KEY `idx_sub_problem` (`problem_id`),
    KEY `idx_sub_sync_batch` (`sync_batch_id`),
    CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_sub_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '做题/提交记录';

-- ------------------------------------------------------------
-- 11. contest —— 比赛基础信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `contest`;
CREATE TABLE `contest` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(200) NOT NULL,
    `platform`      VARCHAR(16)  NOT NULL,
    `url`           VARCHAR(500)          DEFAULT NULL,
    `external_id`   VARCHAR(64)           DEFAULT NULL,
    -- 等级决定参与分，具体分值在 score_rule 里配
    `level`         VARCHAR(16)  NOT NULL DEFAULT 'OTHER' COMMENT 'S / A / B / C / OTHER',
    `start_at`      DATETIME              DEFAULT NULL,
    `end_at`        DATETIME              DEFAULT NULL,
    `created_by`    BIGINT       NOT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_contest_start` (`start_at`),
    KEY `idx_contest_level` (`level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '比赛';

-- ------------------------------------------------------------
-- 12. team_contest —— 队伍参赛选择
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `team_contest`;
CREATE TABLE `team_contest` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `team_id`    BIGINT      NOT NULL,
    `contest_id` BIGINT      NOT NULL,
    `selected_by` BIGINT     NOT NULL                   COMMENT '选择人（队长或站主）',
    `status`     VARCHAR(16) NOT NULL DEFAULT 'SELECTED' COMMENT 'SELECTED / CANCELLED',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tc_team_contest` (`team_id`, `contest_id`),
    KEY `idx_tc_contest` (`contest_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '队伍参赛选择';

-- ------------------------------------------------------------
-- 13. contest_log —— 比赛日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `contest_log`;
CREATE TABLE `contest_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `contest_id`    BIGINT       NOT NULL,
    `participant_type` VARCHAR(16) NOT NULL DEFAULT 'TEAM',
    `user_id`       BIGINT                DEFAULT NULL,
    `team_id`       BIGINT                DEFAULT NULL,
    -- 参赛成员存 JSON 数组（人数少、只读展示，不做关联查询，够用）
    `member_ids`    JSON                  DEFAULT NULL,
    `rank_result`   VARCHAR(64)           DEFAULT NULL   COMMENT '名次，如 12/230',
    `solved_count`  INT                   DEFAULT NULL   COMMENT '通过题数',
    `ac_count`      INT                   DEFAULT NULL,
    `duration_seconds` INT               DEFAULT NULL,
    `penalty_minutes` INT                DEFAULT NULL,
    `summary`       TEXT                  DEFAULT NULL   COMMENT '赛后总结',
    `problem_list_id` BIGINT              DEFAULT NULL   COMMENT '赛后关联题单',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED（队员提交需队长确认）',
    `created_by`    BIGINT       NOT NULL,
    `confirmed_by`  BIGINT                DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_cl_contest` (`contest_id`),
    KEY `idx_cl_team` (`team_id`),
    KEY `idx_cl_participant` (`participant_type`, `user_id`),
    CONSTRAINT `fk_cl_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`),
    CONSTRAINT `fk_cl_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '比赛日志';

-- ------------------------------------------------------------
-- 14. score_rule —— 积分规则（站主可调分值）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `score_rule`;
CREATE TABLE `score_rule` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `event_type`  VARCHAR(32) NOT NULL                COMMENT '见下方枚举',
    `label`       VARCHAR(64) NOT NULL,
    `score`       DECIMAL(8,2) NOT NULL,
    -- 比赛等级细分（如 contest_join 时按 level 取不同分值）
    `sub_key`     VARCHAR(32)          DEFAULT NULL   COMMENT '子键，如比赛等级 S/A/B',
    `enabled`     TINYINT(1)  NOT NULL DEFAULT 1,
    `description` VARCHAR(255)         DEFAULT NULL,
    `updated_by`  BIGINT               DEFAULT NULL,
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 同一事件类型 + 子键只有一条规则
    UNIQUE KEY `uk_rule_type_sub` (`event_type`, `sub_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分规则';

-- ------------------------------------------------------------
-- 15. score_event —— 积分流水【核心】
--    不可覆盖、不可修改；撤销用反向事件（score 为负）
--    idempotent_key 唯一索引是防重复发放的最后一道防线
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `score_event`;
CREATE TABLE `score_event` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT      NOT NULL,
    `event_type`      VARCHAR(32) NOT NULL,
    -- ⚠️ 用 DECIMAL 而不是 INT：难度分档可能出小数（如提高 3.5 分），
    --    INT 会直接存不进去。低精度不要在这上面省。
    `score`           DECIMAL(8,2) NOT NULL             COMMENT '可正可负，负数为撤销/扣分',
    -- 关联业务对象，用于追溯与反向事件
    `biz_type`        VARCHAR(32)          DEFAULT NULL COMMENT 'PROBLEM / ASSIGNMENT / CONTEST / PROBLEM_LIST',
    `biz_id`          BIGINT               DEFAULT NULL,
    `reason`          VARCHAR(255)         DEFAULT NULL,
    -- 幂等键：如 first_ac:12:345 / daily:45:12 / assignment:7:12
    -- 业务层先查已存在则跳过，DB 唯一索引兜底防并发
    `idempotent_key`  VARCHAR(128) NOT NULL,
    -- 反向事件指向被撤销的原事件
    `reversed_event_id` BIGINT             DEFAULT NULL,
    `operator_id`     BIGINT               DEFAULT NULL COMMENT '操作人，系统自动为 NULL',
    `occurred_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_idempotent` (`idempotent_key`),
    KEY `idx_event_user_time` (`user_id`, `occurred_at`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_event_biz` (`biz_type`, `biz_id`),
    CONSTRAINT `fk_event_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水（不可覆盖）';

-- ------------------------------------------------------------
-- 16. user_score_summary —— 个人积分汇总
--    由 score_event 计算而来，是缓存性质的汇总值
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_score_summary`;
CREATE TABLE `user_score_summary` (
    `user_id`         BIGINT   NOT NULL,
    `total_score`     DECIMAL(10,2) NOT NULL DEFAULT 0,
    `solved_count`    INT      NOT NULL DEFAULT 0       COMMENT '去重后的通过题数',
    `contest_count`   INT      NOT NULL DEFAULT 0,
    `daily_count`     INT      NOT NULL DEFAULT 0,
    `assignment_count` INT     NOT NULL DEFAULT 0,
    `last_event_at`   DATETIME          DEFAULT NULL,
    `recalc_at`       DATETIME          DEFAULT NULL   COMMENT '最后重算时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    KEY `idx_uss_score` (`total_score` DESC),
    CONSTRAINT `fk_uss_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个人积分汇总';

-- ------------------------------------------------------------
-- 17. award —— 个人获奖记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `award`;
CREATE TABLE `award` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT       NOT NULL,
    `award_name`     VARCHAR(128) NOT NULL,
    `contest_name`   VARCHAR(200)          DEFAULT NULL,
    `award_level`    VARCHAR(32)           DEFAULT NULL   COMMENT '国家级/省级/校级/金奖等',
    `awarded_at`     DATE                  DEFAULT NULL,
    `cert_url`       VARCHAR(500)          DEFAULT NULL   COMMENT '证书图片或链接',
    `note`           VARCHAR(500)          DEFAULT NULL,
    -- 是否公开展示：队员自己控制，但私密档用户对外始终不展示
    `is_public`      TINYINT(1)   NOT NULL DEFAULT 1,
    `audit_status`   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    `audited_by`     BIGINT                DEFAULT NULL,
    `audited_at`     DATETIME              DEFAULT NULL,
    `reject_reason`  VARCHAR(255)          DEFAULT NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_award_user` (`user_id`, `audit_status`),
    CONSTRAINT `fk_award_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个人获奖记录';

CREATE TABLE `award_certificate` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `award_id`     BIGINT       NOT NULL,
    `file_name`    VARCHAR(255) NOT NULL,
    `content_type` VARCHAR(100) NOT NULL,
    `file_size`    BIGINT       NOT NULL,
    `content`      MEDIUMBLOB  NOT NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_award_certificate_award` (`award_id`),
    CONSTRAINT `fk_award_certificate_award` FOREIGN KEY (`award_id`) REFERENCES `award` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '获奖证书文件';

-- ------------------------------------------------------------
-- 18. announcement —— 公告（全站 / 队伍）
--     用户在第二轮需求中明确要求保留公告，且分两级
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `scope`       VARCHAR(16)  NOT NULL                COMMENT 'GLOBAL（全站）/ TEAM（队伍）',
    `team_id`     BIGINT                DEFAULT NULL   COMMENT 'scope=TEAM 时必填',
    `title`       VARCHAR(200) NOT NULL,
    `content`     TEXT         NOT NULL                COMMENT 'Markdown 正文',
    `pinned`      TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '置顶',
    `published`   TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '草稿/已发布',
    `created_by`  BIGINT       NOT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ann_scope_time` (`scope`, `published`, `created_at`),
    KEY `idx_ann_team` (`team_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公告';

-- ------------------------------------------------------------
-- 19. article —— 博客文章（Markdown 固定格式）
--     用户在第二轮需求中要求新增博客区
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `author_id`    BIGINT       NOT NULL,
    `title`        VARCHAR(200) NOT NULL,
    `summary`      VARCHAR(500)          DEFAULT NULL,
    `content_md`   MEDIUMTEXT   NOT NULL                COMMENT 'Markdown 源码',
    `tags`         VARCHAR(255)          DEFAULT NULL   COMMENT '逗号分隔',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT / PUBLISHED',
    `view_count`   INT          NOT NULL DEFAULT 0,
    `published_at` DATETIME              DEFAULT NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_article_author` (`author_id`),
    KEY `idx_article_status_time` (`status`, `published_at`),
    CONSTRAINT `fk_article_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客文章';

CREATE TABLE IF NOT EXISTS article_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_like_user (article_id, user_id),
    KEY idx_article_like_user (user_id),
    CONSTRAINT fk_article_like_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_like_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS article_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_favorite_user (article_id, user_id),
    KEY idx_article_favorite_user_time (user_id, created_at, id),
    CONSTRAINT fk_article_favorite_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_favorite_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS article_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_comment_time (article_id, created_at, id),
    KEY idx_article_comment_user (user_id),
    CONSTRAINT fk_article_comment_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_comment_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
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

-- ------------------------------------------------------------
-- 20. short_link —— 工具：粘贴板短链
--     用户在第二轮需求中要求工具区保留「粘贴板（生成链接分享）」
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `short_link`;
CREATE TABLE `short_link` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(16)  NOT NULL                COMMENT '短码',
    `title`       VARCHAR(128)          DEFAULT NULL,
    -- 支持分享「文本」或「代码」
    `content_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / CODE',
    `content`     MEDIUMTEXT   NOT NULL,
    `language`    VARCHAR(32)           DEFAULT NULL   COMMENT 'content_type=CODE 时',
    `created_by`  BIGINT                DEFAULT NULL   COMMENT '未登录也可生成则允许 NULL',
    `expires_at`  DATETIME              DEFAULT NULL   COMMENT 'NULL = 永久',
    `view_count`  INT          NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_short_code` (`code`),
    KEY `idx_short_expires` (`expires_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链分享（工具区）';

-- ------------------------------------------------------------
-- 21. audit_log —— 操作日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `operator_id` BIGINT                DEFAULT NULL,
    `operator_name` VARCHAR(32)         DEFAULT NULL   COMMENT '冗余存名字，用户改名也不影响历史',
    `action`      VARCHAR(64)  NOT NULL                COMMENT '如 TEAM_CREATE / LEADER_CHANGE / SCORE_ADJUST',
    `target_type` VARCHAR(32)           DEFAULT NULL,
    `target_id`   BIGINT                DEFAULT NULL,
    -- 变更前后：存 JSON，便于追责与回滚分析
    `before_json` JSON                  DEFAULT NULL,
    `after_json`  JSON                  DEFAULT NULL,
    `detail`      VARCHAR(500)          DEFAULT NULL,
    `ip`          VARCHAR(64)           DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_operator_time` (`operator_id`, `created_at`),
    KEY `idx_audit_action` (`action`),
    KEY `idx_audit_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

-- ------------------------------------------------------------
-- 22. difficulty_score —— 难度 → 分值对照表（按平台配置）
--     为什么单独建表：不同平台难度口径完全不同（洛谷按档位名、CF 按 rating 分段），
--     而且分值属于**教学策略**，站主应当能自己调，调分值不该改代码重新部署。
--     查询优先级：精确档位 → 该平台 is_default 兜底档 → score_rule 里 FIRST_AC 的配置
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `difficulty_score`;
CREATE TABLE `difficulty_score` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `platform`       VARCHAR(16)  NOT NULL COMMENT 'CODEFORCES/ATCODER/NOWCODER/LEETCODE/LUOGU/OTHER',
    `difficulty_key` VARCHAR(32)  NOT NULL COMMENT '该平台内的档位标识，如 普及/提高− 或 1300-1699',
    `label`          VARCHAR(64)  NOT NULL COMMENT '界面显示名',
    `score`          DECIMAL(8,2) NOT NULL COMMENT '该档位的首次完成分值',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '由易到难，下拉排序用',
    `is_default`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '该平台兜底档：难度不在表里时用它',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diff_platform_key` (`platform`, `difficulty_key`),
    KEY `idx_diff_platform_sort` (`platform`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '难度→分值对照表（站主可改）';
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
