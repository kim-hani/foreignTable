package OneTwo.SmartWaiting.domain.waiting.scheduler;

import OneTwo.SmartWaiting.common.scheduler.SchedulerLock;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.store.service.StoreStatisticService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingConnectionCloseEvent;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingMessageEvent;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupSchedulerTest {

    @InjectMocks
    private CleanupScheduler cleanupScheduler;

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StoreStatisticService storeStatisticService;

    @Mock
    private SchedulerLock schedulerLock;

    private void givenLockAcquired(boolean acquired) {
        when(schedulerLock.tryAcquire(eq("cleanupGhostWaitings"), any(Duration.class))).thenReturn(acquired);
    }

    private Waiting ghostWaiting(Long memberId) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        Waiting waiting = mock(Waiting.class);
        when(waiting.getMember()).thenReturn(member);
        return waiting;
    }

    @Test
    @DisplayName("유령 웨이팅 정리 - 취소 처리 후 회원마다 안내 메시지 → 연결 종료 순으로 이벤트를 발행한다.")
    void cleanupGhostWaitings_SendsMessageBeforeClosingConnection() {
        // given
        givenLockAcquired(true);
        Waiting waiting1 = ghostWaiting(1L);
        Waiting waiting2 = ghostWaiting(2L);
        when(waitingRepository.findAllByStatusIn(List.of(WaitingStatus.WAITING, WaitingStatus.CALL)))
                .thenReturn(List.of(waiting1, waiting2));

        // when
        cleanupScheduler.cleanupGhostWaitings();

        // then
        verify(waiting1).changeStatus(WaitingStatus.CANCEL);
        verify(waiting2).changeStatus(WaitingStatus.CANCEL);

        // 커밋 후 리스너가 발행 순서대로 처리하므로 메시지가 연결 종료보다 먼저 발행돼야 함
        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof WaitingMessageEvent m && m.memberId().equals(1L)));
        inOrder.verify(eventPublisher).publishEvent(new WaitingConnectionCloseEvent(1L));
        inOrder.verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof WaitingMessageEvent m && m.memberId().equals(2L)));
        inOrder.verify(eventPublisher).publishEvent(new WaitingConnectionCloseEvent(2L));
    }

    @Test
    @DisplayName("유령 웨이팅 없음 - 알림을 보내거나 연결을 종료하지 않는다.")
    void cleanupGhostWaitings_NoGhostWaitings_SendsNothing() {
        // given
        givenLockAcquired(true);
        when(waitingRepository.findAllByStatusIn(List.of(WaitingStatus.WAITING, WaitingStatus.CALL)))
                .thenReturn(List.of());

        // when
        cleanupScheduler.cleanupGhostWaitings();

        // then
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("락 획득 실패 - 다른 인스턴스가 실행 중이면 정리 작업을 건너뛴다.")
    void cleanupGhostWaitings_LockHeldByOtherInstance_Skips() {
        // given
        givenLockAcquired(false);

        // when
        cleanupScheduler.cleanupGhostWaitings();

        // then
        verify(waitingRepository, never()).findAllByStatusIn(any());
        verify(storeStatisticService, never()).calculateAndSaveStoreStats(anyLong());
    }
}
