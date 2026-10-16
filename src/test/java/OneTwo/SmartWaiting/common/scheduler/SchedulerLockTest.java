package OneTwo.SmartWaiting.common.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerLockTest {

    @InjectMocks
    private SchedulerLock schedulerLock;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @BeforeEach
    void setUp() {
        when(redissonClient.getLock("scheduler:lock:job")).thenReturn(lock);
    }

    @Test
    @DisplayName("락 획득 성공 - 대기 없이 holdFor 동안 점유하고, 작업 후에도 해제하지 않는다.")
    void tryAcquire_Available_AcquiresWithoutWaitAndKeepsLock() throws InterruptedException {
        // given
        when(lock.tryLock(0, 50_000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        // when
        boolean acquired = schedulerLock.tryAcquire("job", Duration.ofSeconds(50));

        // then
        assertThat(acquired).isTrue();
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 - 다른 인스턴스가 점유 중이면 false를 반환한다.")
    void tryAcquire_HeldByOther_ReturnsFalse() throws InterruptedException {
        // given
        when(lock.tryLock(0, 50_000L, TimeUnit.MILLISECONDS)).thenReturn(false);

        // when
        boolean acquired = schedulerLock.tryAcquire("job", Duration.ofSeconds(50));

        // then
        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("인터럽트 - 락 대기 중 인터럽트되면 false를 반환하고 인터럽트 상태를 복원한다.")
    void tryAcquire_Interrupted_ReturnsFalseAndRestoresInterrupt() throws InterruptedException {
        // given
        when(lock.tryLock(0, 50_000L, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        // when
        boolean acquired = schedulerLock.tryAcquire("job", Duration.ofSeconds(50));

        // then
        assertThat(acquired).isFalse();
        assertThat(Thread.interrupted()).isTrue(); // 확인과 동시에 플래그 초기화
    }
}
