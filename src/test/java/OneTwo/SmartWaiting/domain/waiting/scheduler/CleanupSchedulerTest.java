package OneTwo.SmartWaiting.domain.waiting.scheduler;

import OneTwo.SmartWaiting.common.scheduler.SchedulerLock;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.store.service.StoreStatisticService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private NotificationService notificationService;

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
    @DisplayName("유령 웨이팅 정리 - 상태 변경 flush 후 안내 메시지를 보내고 나서 연결을 종료한다.")
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

        InOrder inOrder = inOrder(waitingRepository, notificationService);
        inOrder.verify(waitingRepository).flush();
        inOrder.verify(notificationService).sendToClient(eq(1L), anyString());
        inOrder.verify(notificationService).closeConnection(1L);
        inOrder.verify(notificationService).sendToClient(eq(2L), anyString());
        inOrder.verify(notificationService).closeConnection(2L);
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
        verify(notificationService, never()).sendToClient(anyLong(), anyString());
        verify(notificationService, never()).closeConnection(anyLong());
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
