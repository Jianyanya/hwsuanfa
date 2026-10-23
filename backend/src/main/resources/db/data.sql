-- 初始化公共积分规则，不创建账号或示例业务数据。
USE `hwsuanfa`;

INSERT IGNORE INTO `score_rule` (`event_type`, `label`, `score`, `sub_key`, `description`) VALUES
    ('FIRST_AC',       '首次完成一道题',   2,   '',  '同一道题只发放一次'),
    ('DAILY_SOLVE',    '完成每日一题',     5,   '',  '每天每人只发放一次'),
    ('ASSIGNMENT_DONE','完成作业',         10,  '',  '按作业 reward 字段优先，此处为兜底默认'),
    ('CONTEST_JOIN_S', '参加 S 级比赛',    15,  'S', '比赛等级参与分'),
    ('CONTEST_JOIN_A', '参加 A 级比赛',    10,  'A', '比赛等级参与分'),
    ('CONTEST_JOIN_B', '参加 B 级比赛',    6,   'B', '比赛等级参与分'),
    ('CONTEST_JOIN_C', '参加 C 级比赛',    3,   'C', '比赛等级参与分'),
    ('CONTEST_RANK',   '比赛取得名次',     20,  '',  '名次奖励单独配置，此处为默认'),
    ('LIST_CONTRIBUTE','提供审核通过的题单', 3,  '',  '贡献题目或题单');

INSERT IGNORE INTO `difficulty_score`
    (`platform`, `difficulty_key`, `label`, `score`, `sort_order`, `is_default`) VALUES
    ('LUOGU', '入门', '入门', 1, 1, 0),
    ('LUOGU', '普及−', '普及−', 1, 2, 0),
    ('LUOGU', '普及/提高−', '普及/提高−', 2, 3, 1),
    ('LUOGU', '普及+/提高', '普及+/提高', 3, 4, 0),
    ('LUOGU', '提高+/省选−', '提高+/省选−', 4, 5, 0),
    ('LUOGU', '省选/NOI−', '省选/NOI−', 5, 6, 0),
    ('LUOGU', 'NOI/NOI+/CTSC', 'NOI/NOI+/CTSC', 6, 7, 0),
    ('CODEFORCES', '800-1299', '800-1299', 1, 1, 0),
    ('CODEFORCES', '1300-1699', '1300-1699（≈普及）', 2, 2, 1),
    ('CODEFORCES', '1700-1999', '1700-1999', 3, 3, 0),
    ('CODEFORCES', '2000-2299', '2000-2299', 4, 4, 0),
    ('CODEFORCES', '2300-2599', '2300-2599', 5, 5, 0),
    ('CODEFORCES', '2600+', '2600+', 6, 6, 0),
    ('ATCODER', 'ABC-A/B', 'ABC-A/B', 1, 1, 0),
    ('ATCODER', 'ABC-C', 'ABC-C', 2, 2, 1),
    ('ATCODER', 'ABC-D', 'ABC-D', 3, 3, 0),
    ('ATCODER', 'ABC-E', 'ABC-E', 4, 4, 0),
    ('ATCODER', 'ABC-F', 'ABC-F', 5, 5, 0),
    ('ATCODER', 'ABC-G/ARC/AGC', 'ABC-G/ARC/AGC', 6, 6, 0),
    ('NOWCODER', '入门', '入门', 1, 1, 0),
    ('NOWCODER', '简单', '简单', 2, 2, 1),
    ('NOWCODER', '中等', '中等', 3, 3, 0),
    ('NOWCODER', '较难', '较难', 4, 4, 0),
    ('NOWCODER', '困难', '困难', 5, 5, 0),
    ('LEETCODE', 'Easy', 'Easy', 1, 1, 0),
    ('LEETCODE', 'Medium', 'Medium', 2, 2, 1),
    ('LEETCODE', 'Hard', 'Hard', 4, 3, 0),
    ('OTHER', '简单', '简单', 1, 1, 0),
    ('OTHER', '中等', '中等', 2, 2, 1),
    ('OTHER', '困难', '困难', 4, 3, 0);
