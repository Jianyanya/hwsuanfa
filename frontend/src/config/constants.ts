/** 全局业务常量 —— 数值与后端保持一致，改这里必须同步改后端 */

/** 一个队伍最多 3 人（用户明确要求，勿改） */
export const TEAM_MAX_MEMBERS = 3;

/** 三档隐私等级 */
export const PRIVACY_LEVELS = [
  {
    value: 'PRIVATE',
    label: '完全私密',
    desc: '仅同队成员与站主可见做题情况',
  },
  {
    value: 'PARTIAL',
    label: '部分私密',
    desc: '展示汇总数据，隐藏详细提交与代码',
  },
  {
    value: 'PUBLIC',
    label: '公开',
    desc: '所有人可见做题记录与提交代码',
  },
] as const;

export type PrivacyLevel = (typeof PRIVACY_LEVELS)[number]['value'];

/** 作业状态（后端按服务器时间计算，前端只做展示） */
export const ASSIGNMENT_STATUS = {
  NOT_STARTED: { label: '未开始', tone: 'muted' },
  IN_PROGRESS: { label: '进行中', tone: 'warning' },
  COMPLETED: { label: '已完成', tone: 'success' },
  OVERDUE: { label: '已逾期', tone: 'destructive' },
  OVERDUE_DONE: { label: '逾期完成', tone: 'warning' },
} as const;

export type AssignmentStatus = keyof typeof ASSIGNMENT_STATUS;

/** 支持的 OJ 平台及其同步方式 */
export const OJ_PLATFORMS = [
  { code: 'CODEFORCES', label: 'Codeforces', sync: 'auto', color: 'oklch(45% .12 265)' },
  { code: 'ATCODER', label: 'AtCoder', sync: 'auto', color: 'oklch(50% .14 200)' },
  { code: 'NOWCODER', label: 'NowCoder', sync: 'manual', color: 'oklch(55% .12 30)' },
  { code: 'LEETCODE', label: 'LeetCode', sync: 'manual', color: 'oklch(58% .15 60)' },
  { code: 'LUOGU', label: '洛谷', sync: 'manual', color: 'oklch(48% .13 120)' },
] as const;

export type OjCode = (typeof OJ_PLATFORMS)[number]['code'];
export type OjSyncMode = (typeof OJ_PLATFORMS)[number]['sync'];
