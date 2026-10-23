/** 队伍相关类型 —— 与后端 TeamVO / TeamBriefVO / UserBriefVO 对齐 */

export type TeamStatus = 'ACTIVE' | 'FROZEN';

export interface TeamMember {
  userId: number;
  username: string;
  nickname: string;
  avatar: string | null;
  leader: boolean;
  joinedAt: string | null;
  /** 仅站主可见 */
  realName: string | null;
  /** 仅站主可见 */
  studentNo: string | null;
}

export interface TeamDetail {
  id: number;
  name: string;
  description: string | null;
  leaderUserId: number | null;
  maxMembers: number;
  memberCount: number;
  status: TeamStatus;
  frozenReason: string | null;
  createdAt: string;
  members: TeamMember[];
  viewerIsLeader: boolean;
  viewerIsSiteOwner: boolean;
  /** 站主或本队队长可编辑 */
  editable: boolean;
}

export interface TeamBrief {
  id: number;
  name: string;
  description: string | null;
  leaderUserId: number | null;
  leaderNickname: string | null;
  maxMembers: number;
  memberCount: number;
  status: TeamStatus;
  frozenReason: string | null;
  full: boolean;
}

export interface UserBrief {
  id: number;
  username: string;
  nickname: string;
  realName: string | null;
  studentNo: string | null;
  role: string;
}
