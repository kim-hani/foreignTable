package OneTwo.SmartWaiting.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 서버를 여러 대 띄워도 @Scheduled 작업이 한 인스턴스에서만 실행되도록 하는 Redisson 기반 락.
 * 작업이 끝나도 락을 풀지 않고 holdFor 동안 유지해서, 같은 실행 주기에 다른 인스턴스가 (시계 오차로 조금 늦게 깨어나도) 재실행하지 못하게 한다.
 * holdFor는 작업 주기보다 짧게 잡아야 다음 주기에 다시 실행된다.
 * 인스턴스별 상태를 다루는 작업(SSE Heartbeat 등)에는 사용하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLock {

    private static final String KEY_PREFIX = "scheduler:lock:";

    private final RedissonClient redissonClient;

    public boolean tryAcquire(String jobName, Duration holdFor) {
        RLock lock = redissonClient.getLock(KEY_PREFIX + jobName);
        try {
            boolean acquired = lock.tryLock(0, holdFor.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.info("다른 인스턴스가 실행 중이므로 스케줄 작업 건너뜀: {}", jobName);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("스케줄 작업 락 획득 중 인터럽트 발생: {}", jobName);
            return false;
        }
    }
}
