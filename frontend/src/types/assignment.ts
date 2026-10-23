/** 作业相关类型 —— 与后端 AssignmentVO / ProblemVO 对齐 */

export type AssignmentType = 'TEAM' | 'GLOBAL' | 'DAILY';

/** 纯时间态（对所有人一样） */
export type TimeStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'OVERDUE';

/**
 * 访问者个人状态。
 *
 * ⚠️ 一律用后端返回的值渲染，不要在前端拿 Date.now() 重算 ——
 *    客户端时钟可改，逾期判定会失准。
 */
export type DisplayStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'OVERDUE'
  | 'OVERDUE_DONE';

export interface ProblemItem {
  id: number;
  title: string;
  platform: string;
  problemNo: string | null;
  url: string | null;
  difficulty: string | null;
  tags: string | null;
  /** 当前访问者是否已完成 */
  done: boolean | null;
  /**
   * 该题按难度档位算出的首次完成分值。
   * 由后端算好返回，前端不要自己按难度推算 —— 分值是站主可配的。
   */
  score: number | null;
}

export interface TeamRef {
  id: number;
  name: string;
}

export interface MyProgress {
  rawStatus: 'IN_PROGRESS' | 'COMPLETED';
  status: DisplayStatus;
  late: boolean;
  completedCount: number;
  totalCount: number;
  completedAt: string | null;
  doneProblemIds: number[];
  /** 该项奖励是否已结算 */
  scored: boolean;
}

export interface AdminStats {
  targetTeams: number;
  targetUsers: number;
  completedUsers: number;
  overdueUsers: number;
}

export interface Assignment {
  id: number;
  type: AssignmentType;
  title: string;
  description: string | null;
  startAt: string;
  dueAt: string;
  scoreReward: number;
  allowLateReward: boolean;
  publishDate: string | null;
  createdAt: string;
  timeStatus: TimeStatus;
  /** 负数表示已过期；前端倒计时直接用它，不要自己算 */
  secondsUntilDue: number | null;
  secondsUntilStart: number | null;
  targets: TeamRef[];
  targetTeamCount: number;
  problems: ProblemItem[];
  totalCount: number;
  mine: MyProgress | null;
  stats: AdminStats | null;
}

export interface ProblemBrief {
  id: number;
  title: string;
  platform: string;
  problemNo: string | null;
  url: string | null;
  /** 该平台内的难度档位标识（如 普及/提高− 、1300-1699） */
  difficulty: string | null;
  tags: string | null;
  done: boolean | null;
  score: number | null;
}

/** 难度档位（来自后台可配的 difficulty_score 表） */
export interface DifficultyTier {
  id?: number;
  platform: string;
  difficultyKey: string;
  label: string;
  score: number;
  sortOrder?: number;
  isDefault?: boolean;
}

export interface AssignmentPayload {
  type: AssignmentType;
  title: string;
  description?: string;
  problemIds: number[];
  /** 从题单导入题目，与 problemIds 合并去重 */
  problemListId?: number;
  teamIds?: number[];
  startAt: string;
  dueAt: string;
  scoreReward?: number;
  allowLateReward?: boolean;
  publishDate?: string;
}

/* ---------------- 题单 ---------------- */

export type ListScope = 'PERSONAL' | 'TEAM';
export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ProblemList {
  id: number;
  name: string;
  description: string | null;
  scope: ListScope;
  teamId: number | null;
  teamName: string | null;
  ownerId: number;
  ownerNickname: string | null;
  auditStatus: AuditStatus;
  rejectReason: string | null;
  problemCount: number;
  /** 列表页为 null，详情页才有 */
  problems: ProblemItem[] | null;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 粘贴解析预览结果（只解析不落库） */
export interface ParsePreview {
  problems: { platform: string; problemNo: string; url: string | null }[];
  /** 没能识别的行，原样回报，便于用户定位 */
  unparsed: string[];
  duplicateCount: number;
}

export interface ProblemListPayload {
  name: string;
  description?: string;
  scope: ListScope;
  teamId?: number;
  problemIds?: number[];
  /** 直接粘一段文本，后端会解析成题号 */
  pasteText?: string;
  defaultPlatform?: string;
}

export interface ImportPayload {
  problemIds?: number[];
  pasteText?: string;
  defaultPlatform?: string;
}

/* ---------------- 比赛 ---------------- */

export type ContestLevel = 'S' | 'A' | 'B' | 'C' | 'OTHER';
/** 由后端按服务器时间算，前端不要自己用 Date.now() 判断 */
export type ContestTimeStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'ENDED';

export interface ContestMember {
  id: number;
  nickname: string;
}

export type ParticipantType = 'TEAM' | 'USER';

export interface ContestLog {
  id: number;
  participantType: ParticipantType;
  teamId: number | null;
  teamName: string | null;
  userId: number | null;
  userNickname: string | null;
  userAvatar: string | null;
  members: ContestMember[];
  /** 官方排名（手动填或从平台抓） */
  rankResult: string | null;
  solvedCount: number | null;
  acCount: number | null;
  /** 比赛用时秒 */
  durationSeconds: number | null;
  /** 罚时分钟 */
  penaltyMinutes: number | null;
  /** 站内排名：AC 降序 → 罚时升序 → 用时升序，队伍与个人混排 */
  siteRank: number | null;
  summary: string | null;
  problemListId: number | null;
  status: 'PENDING' | 'CONFIRMED';
  createdBy: string | null;
  createdAt: string;
}

export interface Contest {
  id: number;
  name: string;
  platform: string;
  url: string | null;
  level: ContestLevel;
  /** 参赛积分（按等级算好，前端直接显示） */
  joinScore: number | null;
  startAt: string | null;
  endAt: string | null;
  createdAt: string;
  timeStatus: ContestTimeStatus;
  secondsUntilStart: number | null;
  secondsUntilEnd: number | null;
  teams: { id: number; name: string }[];
  teamCount: number;
  /** 本队是否已选参赛 */
  myTeamSelected: boolean | null;
  /** 我的参赛身份：TEAM（本队）/ USER（个人），可同时有 */
  myRoles: string[] | null;
  /** 是否允许个人报名（本队没报名且我没报过时才允许） */
  individualJoinAllowed: boolean | null;
  logs: ContestLog[] | null;
}

export interface ContestPayload {
  name: string;
  platform: string;
  url?: string;
  level: ContestLevel;
  startAt?: string;
  endAt?: string;
}

export interface ContestLogPayload {
  /** 队伍参赛时的队伍 */
  teamId?: number;
  /** 个人参赛时的选手（自己填自己时可不传） */
  userId?: number;
  participantType?: 'TEAM' | 'USER';
  memberIds?: number[];
  rankResult?: string;
  solvedCount?: number;
  acCount?: number;
  durationSeconds?: number;
  penaltyMinutes?: number;
  summary?: string;
  problemListId?: number;
}

/* ---------------- 个人资料 / OJ 账号绑定 ---------------- */

export interface Profile {
  id: number;
  username: string;
  nickname: string;
  realName: string | null;
  studentNo: string | null;
  className: string | null;
  avatar: string | null;
  email: string | null;
  privacyLevel: string | null;
  showCode: boolean;
  qq: string | null;
  wechat: string | null;
  phone: string | null;
  cfHandle: string | null;
  atcoderHandle: string | null;
  luoguHandle: string | null;
  leetcodeHandle: string | null;
  nowcoderHandle: string | null;
  qojHandle: string | null;
}

export interface HandlePayload {
  cfHandle?: string;
  atcoderHandle?: string;
  luoguHandle?: string;
  leetcodeHandle?: string;
  nowcoderHandle?: string;
  qojHandle?: string;
}

/** CF handle 在线校验结果 */
export interface CfVerifyResult {
  exists: boolean;
  handle: string;
  rating: number | null;
  rank: string | null;
  message: string | null;
}

/** CF 绑定挑战：把验证码临时填进 CF 个人资料的 First/Last Name 以证明归属 */
export interface CfBindChallenge {
  code: string;
  handle: string;
  editUrl: string;
  expiresInSeconds: number;
}

/* ---------------- 提交记录同步 ---------------- */

/** 爬虫/手动导入的同步结果 */
export interface SyncResult {
  platform: string;
  handle: string;
  fetched: number;
  inserted: number;
  firstAcGranted: number;
  batchId: string;
}

/** 我的 AC 提交记录（爬虫同步 + 洛谷导入） */
export interface MySubmission {
  id: number;
  problemId?: number | null;
  platform: string;
  problemNo: string;
  submissionUrl: string | null;
  result: string;
  isFirstAc: boolean;
  submittedAt: string;
  source: 'MANUAL' | 'AUTO';
}

/* ---------------- 积分排行榜 ---------------- */

export interface RankUserRow {
  rank: number;
  userId: number;
  nickname: string;
  className: string | null;
  totalScore: number;
  solvedCount: number;
  firstAc: number;
  dailyCount: number;
  assignmentCount: number;
  contestCount: number;
  teamName: string | null;
}

export interface RankTeamRow {
  rank: number;
  teamId: number;
  teamName: string;
  leaderNickname: string | null;
  memberCount: number;
  totalScore: number;
  avgScore: number;
}

export interface RankData {
  users: RankUserRow[];
  teams: RankTeamRow[];
}

/** 积分明细事件 */
export interface ScoreEventRow {
  eventType: string;
  score: number;
  reason: string;
  createdAt: string;
}

/* ---------------- 获奖记录 ---------------- */

export interface AwardRow {
  id: number;
  userId: number;
  userNickname: string | null;
  awardName: string;
  contestName: string | null;
  awardLevel: string | null;
  awardedAt: string | null;
  certUrl: string | null;
  certificateAvailable?: boolean;
  note: string | null;
  isPublic: boolean;
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectReason: string | null;
  createdAt: string;
}
