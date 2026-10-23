package com.hwsuanfa.algo.dto;

import lombok.Data;

@Data
public class RemoveMemberRequest {

    /**
     * 若被移除者正是当前队长，必须在这里指定新队长 —— 否则拒绝。
     *
     * 为什么不自动转移：队长身份影响权限，站主/操作者应当明确知道交给了谁。
     */
    private Long newLeaderId;
}
