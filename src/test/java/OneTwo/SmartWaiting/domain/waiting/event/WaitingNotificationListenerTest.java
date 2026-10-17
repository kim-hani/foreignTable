package OneTwo.SmartWaiting.domain.waiting.event;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitingNotificationListenerTest {

    private static final Long STORE_ID = 1L;

    @InjectMocks
    private WaitingNotificationListener listener;

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private FcmService fcmService;

    private Waiting waitingOf(Long memberId, String fcmToken, boolean stubToken) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        if (stubToken) {
            when(member.getFcmToken()).thenReturn(fcmToken);
        }
        Waiting waiting = mock(Waiting.class);
        when(waiting.getMember()).thenReturn(member);
        return waiting;
    }

    private void givenWaitingAt(int index, Page<Waiting> page) {
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(STORE_ID), eq(WaitingStatus.WAITING), argThat(p -> p != null && p.getOffset() == index)))
                .thenReturn(page);
    }

    @Test
    @DisplayName("메시지 이벤트 - 해당 회원에게 SSE 메시지를 보낸다.")
    void onMessage_SendsToMember() {
        listener.onMessage(new WaitingMessageEvent(100L, "호출"));

        verify(notificationService).sendToClient(100L, "호출");
    }

    @Test
    @DisplayName("연결 종료 이벤트 - 해당 회원의 SSE 연결을 종료한다.")
    void onConnectionClose_ClosesConnection() {
        listener.onConnectionClose(new WaitingConnectionCloseEvent(100L));

        verify(notificationService).closeConnection(100L);
    }

    @Test
    @DisplayName("대기열 변경 - 새 1번째에게 SSE와 FCM, 새 3번째에게 SSE를 보낸다.")
    void onQueueChanged_NotifiesNewFirstAndThird() {
        // given
        Waiting first = waitingOf(200L, "token-200", true);
        Waiting third = waitingOf(400L, null, false);
        givenWaitingAt(0, new PageImpl<>(List.of(first)));
        givenWaitingAt(2, new PageImpl<>(List.of(third)));

        // when
        listener.onQueueChanged(new WaitingQueueChangedEvent(STORE_ID, true, true));

        // then
        verify(notificationService).sendToClient(200L, WaitingNotificationListener.FIRST_IN_LINE_MESSAGE);
        verify(fcmService).sendFirstInLinePush("token-200", STORE_ID);
        verify(notificationService).sendToClient(400L, WaitingNotificationListener.THIRD_IN_LINE_MESSAGE);
    }

    @Test
    @DisplayName("대기열 변경 - 1번째 알림 대상이 아니면 FCM을 보내지 않고, 3번째가 없으면 SSE도 보내지 않는다.")
    void onQueueChanged_ThirdOnlyAndEmpty_SendsNothing() {
        // given
        givenWaitingAt(2, Page.empty());

        // when
        listener.onQueueChanged(new WaitingQueueChangedEvent(STORE_ID, true, false));

        // then
        verify(fcmService, never()).sendFirstInLinePush(any(), any());
        verify(notificationService, never()).sendToClient(anyLong(), anyString());
    }

    @Test
    @DisplayName("알림 실패 - 커밋 이후라 예외를 밖으로 던지지 않고 로그만 남긴다.")
    void onMessage_SendFails_DoesNotPropagate() {
        doThrow(new IllegalStateException("redis down")).when(notificationService).sendToClient(anyLong(), anyString());

        assertThatCode(() -> listener.onMessage(new WaitingMessageEvent(100L, "호출"))).doesNotThrowAnyException();
    }
}
