package OneTwo.SmartWaiting.domain.notification.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final String EMAIL = "user@test.com";

    private MemberRepository memberRepository;
    private NotificationService notificationService;
    private Map<Long, SseEmitter> emitters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        notificationService = new NotificationService(memberRepository);
        emitters = (Map<Long, SseEmitter>) ReflectionTestUtils.getField(notificationService, "emitters");
    }

    private void givenMember(Long memberId) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("구독 성공 - JWT 인증 사용자(email)의 회원 ID로 emitter를 등록한다.")
    void subscribe_AuthenticatedEmail_RegistersByMemberId() {
        // given
        givenMember(1L);

        // when
        SseEmitter emitter = notificationService.subscribe(EMAIL);

        // then
        assertThat(emitters).containsEntry(1L, emitter).hasSize(1);
    }

    @Test
    @DisplayName("구독 실패 - 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외가 발생한다.")
    void subscribe_UnknownEmail_ThrowsMemberNotFound() {
        // given
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.subscribe(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(emitters).isEmpty();
    }

    @Test
    @DisplayName("재구독 - 이전 emitter는 종료되고 Map에는 새 emitter만 남는다.")
    void subscribe_Resubscribe_CompletesPreviousAndKeepsNew() {
        // given
        givenMember(1L);
        SseEmitter previous = mock(SseEmitter.class);
        emitters.put(1L, previous);

        // when
        SseEmitter current = notificationService.subscribe(EMAIL);

        // then
        verify(previous, times(1)).complete();
        assertThat(emitters).containsEntry(1L, current);
        assertThat(current).isNotSameAs(previous);
    }

    @Test
    @DisplayName("알림 전송 실패 - 실패한 emitter가 이미 교체됐다면 새 emitter는 지우지 않는다.")
    void sendToClient_FailedEmitterReplaced_KeepsNewEmitter() throws IOException {
        // given
        SseEmitter stale = mock(SseEmitter.class);
        SseEmitter fresh = mock(SseEmitter.class);
        doAnswer(invocation -> {
            emitters.put(1L, fresh); // 전송 도중 재연결로 교체된 상황
            throw new IOException("Broken pipe");
        }).when(stale).send(any(SseEmitter.SseEventBuilder.class));
        emitters.put(1L, stale);

        // when
        notificationService.sendToClient(1L, "알림");

        // then
        assertThat(emitters).containsEntry(1L, fresh);
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
