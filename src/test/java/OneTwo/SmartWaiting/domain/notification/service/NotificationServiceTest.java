package OneTwo.SmartWaiting.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private NotificationService notificationService;
    private Map<Long, SseEmitter> emitters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        notificationService = new NotificationService();
        emitters = (Map<Long, SseEmitter>) ReflectionTestUtils.getField(notificationService, "emitters");
    }

    @Test
    @DisplayName("Heartbeat 성공 - 연결된 모든 emitter에 heartbeat를 보내고 연결을 유지한다.")
    void sendHeartbeat_AliveEmitters_KeepsConnections() throws IOException {
        // given
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        emitters.put(1L, emitter1);
        emitters.put(2L, emitter2);

        // when
        notificationService.sendHeartbeat();

        // then
        verify(emitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitters).containsKeys(1L, 2L);
    }

    @Test
    @DisplayName("Heartbeat 실패 - 전송 중 IOException이 나면 죽은 연결만 Map에서 제거한다.")
    void sendHeartbeat_DeadEmitter_RemovesOnlyDeadConnection() throws IOException {
        // given
        SseEmitter alive = mock(SseEmitter.class);
        SseEmitter dead = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
        emitters.put(1L, alive);
        emitters.put(2L, dead);

        // when
        notificationService.sendHeartbeat();

        // then
        assertThat(emitters).containsKey(1L).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("Heartbeat 실패 - 이미 완료된 emitter(IllegalStateException)도 Map에서 제거한다.")
    void sendHeartbeat_CompletedEmitter_RemovesConnection() throws IOException {
        // given
        SseEmitter completed = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(completed).send(any(SseEmitter.SseEventBuilder.class));
        emitters.put(1L, completed);

        // when
        notificationService.sendHeartbeat();

        // then
        assertThat(emitters).isEmpty();
    }

    @Test
    @DisplayName("연결 없음 - 구독자가 없으면 아무 일도 일어나지 않는다.")
    void sendHeartbeat_NoEmitters_DoesNothing() {
        // when
        notificationService.sendHeartbeat();

        // then
        assertThat(emitters).isEmpty();
    }
}
