package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.service.ChatRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String EMAIL = "user@test.com";

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private MessageChannel channel;

    private Message<byte[]> stompMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ================= [ CONNECT 인증 ] =================

    @Test
    @DisplayName("CONNECT 성공 - 유효한 JWT면 email/role 기반 Principal을 세션에 심는다.")
    void preSend_ConnectWithValidToken_SetsPrincipal() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getEmail("valid-token")).thenReturn(EMAIL);
        when(jwtTokenProvider.getRole("valid-token")).thenReturn("USER");

        // when
        Message<?> result = interceptor.preSend(stompMessage(accessor), channel);

        // then
        StompHeaderAccessor out = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(out.getUser()).isNotNull();
        assertThat(out.getUser().getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("CONNECT 실패 - 유효하지 않은 토큰이면 INVALID_TOKEN 예외가 발생한다.")
    void preSend_ConnectWithInvalidToken_ThrowsInvalidToken() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer bad-token");
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preSend(stompMessage(accessor), channel));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("CONNECT 실패 - Authorization 헤더가 없으면 INVALID_TOKEN 예외가 발생한다.")
    void preSend_ConnectWithoutAuthorizationHeader_ThrowsInvalidToken() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preSend(stompMessage(accessor), channel));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    // ================= [ SUBSCRIBE 인가 ] =================

    @Test
    @DisplayName("SUBSCRIBE 성공 - 채팅방 목적지면 방 접근 권한 검증을 위임한다.")
    void preSend_SubscribeChatDestination_CallsValidateSubscription() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/chat/room/5");
        accessor.setUser(new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));

        // when
        interceptor.preSend(stompMessage(accessor), channel);

        // then
        verify(chatRoomService).validateSubscription(EMAIL, 5L);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - 채팅방 외 목적지는 ACCESS_DENIED 예외가 발생한다.")
    void preSend_SubscribeInvalidDestination_ThrowsAccessDenied() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/waiting/1");
        accessor.setUser(new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preSend(stompMessage(accessor), channel));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - 인증되지 않은 세션이면 INVALID_TOKEN 예외가 발생한다.")
    void preSend_SubscribeWithoutPrincipal_ThrowsInvalidToken() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/chat/room/5");

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preSend(stompMessage(accessor), channel));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
