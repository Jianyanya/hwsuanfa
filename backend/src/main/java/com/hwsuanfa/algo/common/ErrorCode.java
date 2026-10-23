package com.hwsuanfa.algo.common;

import lombok.Getter;

/**
 * 业务错误码。
 * 前端 lib/http.ts 依赖 HTTP 状态码做跳转，这里的 code 只用于业务细分。
 */
@Getter
public enum ErrorCode {

    /* ---- 通用 ---- */
    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    GONE(410, "内容已过期"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    /* ---- 认证 ---- */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    TOKEN_INVALID(1003, "令牌无效"),
    TOKEN_EXPIRED(1004, "令牌已过期"),
    REFRESH_TOKEN_INVALID(1005, "刷新令牌无效或已失效"),
    LOGIN_ATTEMPT_LIMIT(1006, "登录尝试次数过多，请稍后再试"),

    /* ---- 队伍 ---- */
    TEAM_NOT_FOUND(2001, "队伍不存在"),
    TEAM_FULL(2002, "队伍已满员"),
    TEAM_FROZEN(2003, "队伍已被冻结"),
    ALREADY_TEAM_MEMBER(2004, "该用户已是队员"),
    NOT_TEAM_MEMBER(2005, "该用户不是队伍成员"),
    LEADER_MUST_BE_MEMBER(2006, "队长必须是本队成员"),
    LEADER_IMMUTABLE(2007, "队长变更只能由站主执行"),
    CANNOT_REMOVE_LAST_LEADER(2008, "队伍仅剩队长一人，请先指定新队长或解散队伍"),
    TEAM_NAME_EXISTS(2009, "队伍名称已存在"),
    MEMBER_LIMIT_EXCEEDED(2010, "成员数已达上限"),
    LEADER_REQUIRED(2011, "请先为本队指定队长"),
    REMOVE_LEADER_NEEDS_TRANSFER(2012, "移除队长前请先指定新队长"),
    LEADER_MUST_BE_OTHER_MEMBER(2013, "新队长必须是本队其他成员"),
    USER_ALREADY_IN_OTHER_TEAM_LIMIT(2014, "该用户所在队伍数已达上限"),
    NOT_TEAM_LEADER(2015, "仅本队队长可执行该操作"),
    USER_NOT_FOUND(2016, "用户不存在"),
    TEAM_HAS_SCORE_HISTORY(2017, "队伍存在历史积分记录，不能删除，请改为冻结"),

    /* ---- 作业 ---- */
    ASSIGNMENT_NOT_FOUND(3001, "作业不存在"),
    ASSIGNMENT_ALREADY_DONE(3002, "该作业已完成"),
    ASSIGNMENT_NOT_STARTED(3003, "作业尚未开始"),
    ASSIGNMENT_EXPIRED(3004, "作业已截止"),
    DAILY_ALREADY_PUBLISHED(3005, "当天已发布过每日一题"),
    PROBLEM_NOT_FOUND(3006, "题目不存在"),
    PROBLEM_DUPLICATE(3007, "该平台的这道题已存在"),
    ASSIGNMENT_NEEDS_PROBLEM(3008, "作业至少需要一道题"),
    NOT_YOUR_ASSIGNMENT(3009, "该作业未指派给你所在的队伍"),
    INVALID_TIME_RANGE(3010, "开始时间必须早于截止时间"),
    DAILY_DATE_REQUIRED(3011, "每日一题必须填写发布日"),
    CONTEST_NOT_FOUND(3012, "比赛不存在"),
    CONTEST_ALREADY_SELECTED(3013, "该队伍已选择参加这场比赛"),
    CONTEST_NOT_SELECTED(3014, "该队伍尚未选择参加这场比赛"),
    CONTEST_LOG_CONFIRMED(3015, "该比赛日志已确认，不能重复确认"),
    NOT_CONTEST_PARTICIPANT(3016, "你不属于参赛队伍"),

    /* ---- 积分 ---- */
    SCORE_ALREADY_GRANTED(4001, "该积分已发放过"),
    SCORE_RULE_NOT_FOUND(4002, "积分规则不存在"),

    /* ---- 隐私 ---- */
    PRIVACY_DENIED(5001, "因对方隐私设置无法查看该内容"),

    /* ---- 注册 ---- */
    REGISTER_DISABLED(6001, "注册功能已关闭，请联系站主"),
    INVITE_CODE_INVALID(6002, "邀请码无效"),
    STUDENT_NO_EXISTS(6003, "该学号已注册，请直接登录"),
    NICKNAME_EXISTS(6004, "该名称已被使用，请换一个"),
    REGISTER_ATTEMPT_LIMIT(6005, "尝试次数过多，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
