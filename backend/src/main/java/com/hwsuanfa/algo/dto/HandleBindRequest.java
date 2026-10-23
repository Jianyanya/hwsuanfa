package com.hwsuanfa.algo.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;

/**
 * 绑定各平台的 OJ 账号（handle / 用户名）。
 *
 * ⚠️ 刻意**不收密码**：抓官方榜单用的是 handle（CF 的公开 API 输 handle 就够），
 * 存别人的 OJ 密码是纯安全负债 —— 等于替全队保管密码，泄露了说不清。
 * 以后若真有需要登录态的功能（如洛谷同步脚本），密码应由本人运行时输入，不落库。
 */
@Data
public class HandleBindRequest {

    @Size(max = 64, message = "Codeforces 用户名不能超过 64 字")
    private String cfHandle;

    @Size(max = 64, message = "AtCoder 用户名不能超过 64 字")
    private String atcoderHandle;

    @Size(max = 64, message = "洛谷账号不能超过 64 字")
    private String luoguHandle;

    @Size(max = 64, message = "LeetCode 用户名不能超过 64 字")
    private String leetcodeHandle;

    @Size(max = 64, message = "NowCoder 用户名不能超过 64 字")
    private String nowcoderHandle;

    @Size(max = 64, message = "QOJ 用户名不能超过 64 字")
    private String qojHandle;
}
