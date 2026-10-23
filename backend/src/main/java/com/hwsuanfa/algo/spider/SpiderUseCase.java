package com.hwsuanfa.algo.spider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.SubmissionImportRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.SubmissionRecordMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.service.ScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 爬虫用例 —— 对应参考架构里的 SpiderUseCase.LoadData()。
 *
 * 职责：拿「平台 + handle」→ 爬虫抓 AC 记录 → 匹配/创建题目 →
 * UPSERT 进提交表 → 首次完成自动发分 → 失效相关缓存。
 *
 * 关于 RabbitMQ：参考架构用 MQ 解耦「业务侧投递」和「爬虫消费」。
 * 本站体量小、且同步是站主/本人点按钮触发，进程内直调 UseCase 就够；
 * UseCase 本身不依赖调用方，以后要上队列，加一个 Consumer 调它即可，不用改这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderUseCase {

    /** 积分汇总的缓存键（失效用；与 ScoreService 约定） */
    public static final String SCORE_SUMMARY_KEY = "score_summary:";

    /** 每日同步限流：每平台每天 3 次（用户要求的防滥用上限） */
    private static final int DAILY_SYNC_LIMIT = 3;
    private static final String SYNC_LIMIT_KEY = "sync_limit:";

    private final SpiderRegistry registry;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionRecordMapper submissionMapper;
    private final ScoreService scoreService;
    private final StringRedisTemplate redis;

    /**
     * 同步某成员在某平台的 AC 提交记录。
     *
     * 流程：绑定的 handle → 爬虫抓取 → 匹配/建题 → 落库 → 首次 AC 发分 → 失效缓存。
     *
     * @return 抓取/入库/首次完成的统计
     */
    @Transactional
    public SyncResult syncSubmissions(Long userId, String platform) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        String normalizedPlatform = normalizePlatform(platform);
        String handle = switch (normalizedPlatform) {
            case "CODEFORCES" -> user.getCfHandle();
            case "ATCODER" -> user.getAtcoderHandle();
            case "NOWCODER" -> user.getNowcoderHandle();
            case "LUOGU" -> user.getLuoguHandle();
            case "LEETCODE" -> user.getLeetcodeHandle();
            case "QOJ" -> user.getQojHandle();
            default -> null;
        };
        if (handle == null || handle.isBlank()) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "请先在个人中心绑定 " + platform + " 的用户名");
        }

        var fetcher = registry.get(normalizedPlatform);
        if (fetcher == null) {
            // 有多少说多少：洛谷/牛客等需要登录 Cookie，暂未接入插件
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "「" + platform + "」的爬虫未接入：该平台需要登录 Cookie 才能抓取，"
                            + "洛谷请使用本站洛谷同步油猴脚本手动同步");
        }

        checkDailyLimit(userId, normalizedPlatform);
        LocalDateTime since = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                        .eq(SubmissionRecord::getUserId, userId)
                        .eq(SubmissionRecord::getPlatform, normalizedPlatform)
                        .orderByDesc(SubmissionRecord::getSubmittedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().map(SubmissionRecord::getSubmittedAt)
                .map(value -> value.minusSeconds(1)).orElse(null);
        List<SubmitLog> logs = fetcher.fetchLogs(handle, since);
        return ingestLogs(user, normalizedPlatform, handle, logs, "AUTO");
    }

    @Transactional
    public SyncResult importSubmissions(Long userId, String platform, SubmissionImportRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        String normalizedPlatform = normalizePlatform(platform);
        String handle = switch (normalizedPlatform) {
            case "CODEFORCES" -> user.getCfHandle();
            case "ATCODER" -> user.getAtcoderHandle();
            case "NOWCODER" -> user.getNowcoderHandle();
            case "LUOGU" -> user.getLuoguHandle();
            case "LEETCODE" -> user.getLeetcodeHandle();
            case "QOJ" -> user.getQojHandle();
            default -> null;
        };
        if (handle == null || handle.isBlank()) {
            if (!"LUOGU".equals(normalizedPlatform) && !"OTHER".equals(normalizedPlatform)) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "手动导入只支持 LUOGU");
            }
            if ("LUOGU".equals(normalizedPlatform)) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "请先在个人中心绑定洛谷用户名");
            }
        }
        List<SubmitLog> logs = request.getRecords().stream()
                .map(item -> new SubmitLog(
                        normalizedPlatform,
                        item.getProblemNo().trim().toUpperCase(),
                        item.getSubmittedAt(),
                        blankToNull(item.getLanguage()),
                        blankToNull(item.getUrl()),
                        "AC"))
                .toList();
        return ingestLogs(user, normalizedPlatform, handle, logs, "MANUAL");
    }

    /**
     * 入库：存**全部提交**（AC/WA/TLE…），不只 AC —— 用户要求热力图与近期动态
     * 反映真实提交行为。
     *
     * 去重键：(user, problem, submittedAt)（唯一索引 uk_sub_user_problem_time 兜底）。
     * 首次完成：只有 result=AC 的记录参与；该用户在该题还没有首次 AC 时记为首次并发分。
     */
    private SyncResult ingestLogs(User user, String platform, String handle,
                                  List<SubmitLog> input, String source) {
        // 按时间升序处理，保证「首次 AC」的判定与真实时间一致
        List<SubmitLog> logs = new ArrayList<>(input);
        logs.sort(Comparator.comparing(SubmitLog::submittedAt));

        String batchId = "sync-" + source.toLowerCase() + "-" + platform + "-" + user.getId() + "-"
                + Long.toHexString(System.currentTimeMillis());

        // 已有记录键集合：userId|problemId|submittedAt，重复同步整条跳过
        Set<String> existing = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                        .eq(SubmissionRecord::getUserId, user.getId())
                        .eq(SubmissionRecord::getPlatform, platform))
                .stream()
                .map(r -> subKey(r.getProblemId(), r.getSubmittedAt()))
                .collect(java.util.stream.Collectors.toSet());

        int inserted = 0;
        int firstAc = 0;

        for (SubmitLog logLine : logs) {
            Problem problem = findOrCreateProblem(platform, logLine.problemNo(), logLine.url(), user.getId());
            if (problem == null) {
                continue;
            }
            String key = subKey(problem.getId(), logLine.submittedAt());
            if (existing.contains(key)) {
                continue;
            }

            boolean isAc = "AC".equals(logLine.result());
            boolean alreadyHasFirstAc = isAc && submissionMapper.selectCount(
                    new LambdaQueryWrapper<SubmissionRecord>()
                            .eq(SubmissionRecord::getUserId, user.getId())
                            .eq(SubmissionRecord::getProblemId, problem.getId())
                            .eq(SubmissionRecord::getIsFirstAc, true)) > 0;

            SubmissionRecord record = new SubmissionRecord();
            record.setUserId(user.getId());
            record.setProblemId(problem.getId());
            record.setPlatform(platform);
            record.setProblemNo(logLine.problemNo());
            record.setSubmissionUrl(logLine.url());
            record.setCodeLanguage(logLine.language());
            record.setResult(logLine.result());
            record.setIsFirstAc(isAc && !alreadyHasFirstAc);
            record.setSubmittedAt(logLine.submittedAt());
            record.setSource(source);
            record.setSyncBatchId(batchId);
            try {
                submissionMapper.insert(record);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (!Boolean.TRUE.equals(record.getIsFirstAc())
                        || submissionMapper.selectCount(new LambdaQueryWrapper<SubmissionRecord>()
                        .eq(SubmissionRecord::getUserId, user.getId())
                        .eq(SubmissionRecord::getProblemId, problem.getId())
                        .eq(SubmissionRecord::getSubmittedAt, logLine.submittedAt())) > 0) {
                    continue;
                }
                record.setIsFirstAc(false);
                try {
                    submissionMapper.insert(record);
                } catch (org.springframework.dao.DuplicateKeyException ignored) {
                    continue;
                }
            }
            existing.add(key);
            inserted++;

            if (Boolean.TRUE.equals(record.getIsFirstAc())) {
                // 首次完成：按难度分档发分（幂等键 first_ac:{user}:{problem}）
                boolean granted = scoreService.grantFirstAc(user.getId(), problem.getId(),
                        platform, problem.getDifficulty(),
                        new BigDecimal("2"));
                if (granted) {
                    firstAc++;
                }
            }
        }

        // 失效缓存：积分汇总按人缓存，同步完立刻失效（下次读会重建）
        redis.delete(SCORE_SUMMARY_KEY + user.getId());

        log.info("爬虫同步完成：user={} platform={} 抓取 {} 条，入库 {}，首次 AC {}（batch={}）",
                user.getNickname(), platform, logs.size(), inserted, firstAc, batchId);
        return new SyncResult(platform, handle, logs.size(), inserted, firstAc, batchId);
    }

    private static String subKey(Long problemId, java.time.LocalDateTime at) {
        return problemId + "|" + at;
    }

    private String normalizePlatform(String platform) {
        String value = platform == null ? "" : platform.trim().toUpperCase();
        if (!Set.of("CODEFORCES", "ATCODER", "NOWCODER", "LEETCODE", "LUOGU", "QOJ", "OTHER").contains(value)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "不支持的平台：" + platform);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 复用「不存在则建占位题目」的公共逻辑（与批量粘贴同一套） */
    private Problem findOrCreateProblem(String platform, String problemNo, String url, Long userId) {
        String p = platform.trim().toUpperCase();
        Problem existing = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPlatform, p)
                .eq(Problem::getProblemNo, problemNo));
        if (existing != null) {
            return existing;
        }
        Problem created = new Problem();
        created.setTitle(p + " " + problemNo);   // 不抓题面，标题用「平台 题号」占位
        created.setPlatform(p);
        created.setProblemNo(problemNo);
        created.setUrl(problemUrl(p, problemNo, url));
        created.setCreatedBy(userId);
        try {
            problemMapper.insert(created);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                    .eq(Problem::getPlatform, p)
                    .eq(Problem::getProblemNo, problemNo));
        }
        return created;
    }

    private String problemUrl(String platform, String problemNo, String submissionUrl) {
        if ("LUOGU".equals(platform)) {
            return "https://www.luogu.com.cn/problem/" + problemNo;
        }
        if ("CODEFORCES".equals(platform)) {
            var match = java.util.regex.Pattern.compile("^(\\d+)([A-Za-z]\\d*)$").matcher(problemNo);
            if (match.matches()) {
                return Long.parseLong(match.group(1)) >= 100000
                        ? "https://codeforces.com/gym/" + match.group(1) + "/problem/" + match.group(2)
                        : "https://codeforces.com/problemset/problem/" + match.group(1) + "/" + match.group(2);
            }
        }
        if ("ATCODER".equals(platform) && submissionUrl != null) {
            var match = java.util.regex.Pattern.compile("https://atcoder\\.jp/contests/([^/]+)/submissions/\\d+")
                    .matcher(submissionUrl);
            if (match.matches()) {
                return "https://atcoder.jp/contests/" + match.group(1) + "/tasks/" + problemNo;
            }
        }
        return null;
    }

    /**
     * 每日同步限流：每平台每天 3 次。
     * Redis 计数 + 当日 24 点过期；爬一次外网成本不低，防止手痒连点。
     * 洛谷的手动导入不走这里（那是用户自己脚本上传，无外部抓取成本）。
     */
    private void checkDailyLimit(Long userId, String platform) {
        String today = java.time.LocalDate.now().toString();
        String key = SYNC_LIMIT_KEY + userId + ":" + platform + ":" + today;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofDays(2));
        if (count != null && count > DAILY_SYNC_LIMIT) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "今天「" + platform + "」已同步 " + DAILY_SYNC_LIMIT + " 次，达到每日上限，明天再试");
        }
    }

    /** 同步结果统计 */
    public record SyncResult(String platform, String handle, int fetched,
                             int inserted, int firstAcGranted, String batchId) {
    }

    /** 我的提交记录（按时间倒序） */
    public List<SubmissionRecord> mySubmissions(Long userId) {
        return submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                .eq(SubmissionRecord::getUserId, userId)
                .orderByDesc(SubmissionRecord::getSubmittedAt));
    }
}
