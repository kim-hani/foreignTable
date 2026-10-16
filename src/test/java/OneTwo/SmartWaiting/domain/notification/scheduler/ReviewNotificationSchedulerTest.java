package OneTwo.SmartWaiting.domain.notification.scheduler;

import OneTwo.SmartWaiting.common.scheduler.SchedulerLock;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewNotificationSchedulerTest {

    @InjectMocks
    private ReviewNotificationScheduler reviewNotificationScheduler;

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private FcmService fcmService;

    @Mock
    private SchedulerLock schedulerLock;

    private void givenLockAcquired(boolean acquired) {
        when(schedulerLock.tryAcquire(eq("sendReviewRequestNotifications"), any(Duration.class))).thenReturn(acquired);
    }

    @Test
    @DisplayName("조회 구간 - 분 단위로 정렬된 1분짜리 [start, end) 구간으로 SEATED 웨이팅을 조회한다.")
    void sendReviewRequestNotifications_QueriesMinuteAlignedOneMinuteWindow() {
        // given
        givenLockAcquired(true);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime before = LocalDateTime.now();

        // when
        reviewNotificationScheduler.sendReviewRequestNotifications();

        // then
        verify(waitingRepository).findAllWithMemberAndStoreByStatusAndUpdatedAtRange(
                eq(WaitingStatus.SEATED), startCaptor.capture(), endCaptor.capture());
        LocalDateTime start = startCaptor.getValue();
        LocalDateTime end = endCaptor.getValue();

        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofMinutes(1));
        assertThat(start.getSecond()).isZero();
        assertThat(start.getNano()).isZero();
        assertThat(end).isBeforeOrEqualTo(before.minusMinutes(59));
    }

    @Test
    @DisplayName("시각 정렬 - cron이 조금 일찍 실행돼도 의도한 분으로 반올림한다.")
    void alignToMinute_EarlyFire_RoundsUpToIntendedMinute() {
        LocalDateTime early = LocalDateTime.of(2026, 10, 16, 18, 3, 59, 869_000_000);

        assertThat(ReviewNotificationScheduler.alignToMinute(early))
                .isEqualTo(LocalDateTime.of(2026, 10, 16, 18, 4));
    }

    @Test
    @DisplayName("시각 정렬 - cron이 조금 늦게 실행돼도 의도한 분으로 내림한다.")
    void alignToMinute_LateFire_RoundsDownToIntendedMinute() {
        LocalDateTime late = LocalDateTime.of(2026, 10, 16, 18, 4, 0, 244_000_000);

        assertThat(ReviewNotificationScheduler.alignToMinute(late))
                .isEqualTo(LocalDateTime.of(2026, 10, 16, 18, 4));
    }

    @Test
    @DisplayName("푸시 발송 - 조회된 대상마다 회원 토큰과 가게 정보로 리뷰 요청 푸시를 보낸다.")
    void sendReviewRequestNotifications_SendsPushPerTarget() {
        // given
        givenLockAcquired(true);
        Member member = mock(Member.class);
        when(member.getFcmToken()).thenReturn("token-1");
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(10L);
        when(store.getName()).thenReturn("맛집");
        Waiting waiting = mock(Waiting.class);
        when(waiting.getMember()).thenReturn(member);
        when(waiting.getStore()).thenReturn(store);
        when(waitingRepository.findAllWithMemberAndStoreByStatusAndUpdatedAtRange(eq(WaitingStatus.SEATED), any(), any()))
                .thenReturn(List.of(waiting));

        // when
        reviewNotificationScheduler.sendReviewRequestNotifications();

        // then
        verify(fcmService).sendReviewRequestPush(eq("token-1"), anyString(), contains("맛집"), eq(10L));
    }

    @Test
    @DisplayName("대상 없음 - 조회 결과가 없으면 푸시를 보내지 않는다.")
    void sendReviewRequestNotifications_NoTargets_SendsNothing() {
        // given
        givenLockAcquired(true);

        // when
        reviewNotificationScheduler.sendReviewRequestNotifications();

        // then
        verify(fcmService, never()).sendReviewRequestPush(any(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("락 획득 실패 - 다른 인스턴스가 이번 분을 처리 중이면 조회·발송을 건너뛴다.")
    void sendReviewRequestNotifications_LockHeldByOtherInstance_Skips() {
        // given
        givenLockAcquired(false);

        // when
        reviewNotificationScheduler.sendReviewRequestNotifications();

        // then
        verify(waitingRepository, never()).findAllWithMemberAndStoreByStatusAndUpdatedAtRange(any(), any(), any());
        verify(fcmService, never()).sendReviewRequestPush(any(), anyString(), anyString(), anyLong());
    }
}
