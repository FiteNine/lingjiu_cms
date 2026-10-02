package com.lingjiuw.cms.module.system.service;

import com.lingjiuw.cms.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：进程内内存实现（项目刻意不引入 Redis，见 README「设计取舍」）。
 *
 * <p>同一「用户名 + 客户端 IP」在失败窗口内连续失败达到阈值后锁定一段时间，成功登录清零。
 * 目的是给公网可达的后台登录入口一个最低成本的暴力破解防线。
 *
 * <p>键里带 IP 是为了避免攻击者用单个 IP 反复失败就把受害者账号锁死；代价是攻击者轮换 IP
 * 会减慢锁定。已知取舍：单实例内存态，应用重启后计数归零；多实例部署时这里要换成共享存储。
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 失败窗口内允许的连续失败次数，达到即锁定 */
    private static final int MAX_FAILURES = 5;
    /** 连续失败的统计窗口 */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    /** 达到阈值后的锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 内存上限：防止攻击者用海量用户名把计数表撑爆，超出时先清理过期条目 */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 登录前调用：处于锁定期直接拒绝，不查库、不比对密码 */
    public void checkAllowed(String key) {
        Attempt attempt = attempts.get(key);
        Instant now = Instant.now();
        if (attempt != null && attempt.lockedUntil() != null && attempt.lockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, attempt.lockedUntil()).toMinutes() + 1);
            throw new BizException("登录失败次数过多，请 " + minutes + " 分钟后再试");
        }
    }

    /** 登录失败（用户名不存在或密码错误）时调用 */
    public void onFailure(String key) {
        attempts.compute(key, (ignored, previous) -> {
            Instant now = Instant.now();
            // 距上次失败已超过窗口，从 1 重新计数
            boolean fresh = previous == null || previous.lastFailure().plus(FAILURE_WINDOW).isBefore(now);
            int failures = fresh ? 1 : previous.failures() + 1;
            if (failures < MAX_FAILURES) {
                return new Attempt(failures, now, null);
            }
            log.warn("登录失败次数过多，已锁定 {} 分钟：{}", LOCK_DURATION.toMinutes(), key);
            return new Attempt(failures, now, now.plus(LOCK_DURATION));
        });
        pruneExpired();
    }

    /** 登录成功时调用：清零该键的失败记录，避免正常用户被历史失败拖累 */
    public void onSuccess(String key) {
        attempts.remove(key);
    }

    private void pruneExpired() {
        if (attempts.size() <= MAX_ENTRIES) {
            return;
        }
        Instant cutoff = Instant.now().minus(FAILURE_WINDOW);
        attempts.entrySet().removeIf(entry -> entry.getValue().lastFailure().isBefore(cutoff));
    }

    private record Attempt(int failures, Instant lastFailure, Instant lockedUntil) {
    }
}