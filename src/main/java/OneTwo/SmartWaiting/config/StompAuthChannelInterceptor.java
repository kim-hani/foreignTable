package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP 프레임 레벨 인증/인가 인터셉터.
 *
 * <p>브라우저 WebSocket API는 핸드셰이크에 커스텀 HTTP 헤더를 실을 수 없으므로
 * (SecurityConfig에서 /ws/**는 permitAll), 인증은 CONNECT 프레임의
 * native header "Authorization: Bearer {JWT}"로 수행한다 — 기존 REST의
 * JwtAuthenticationFilter와 동일한 토큰/검증 로직(JwtTokenProvider) 재사용.
 *
 * <p>SUBSCRIBE는 "/sub/chat/room/{roomId}" 목적지만 허용하고, 방 참여자(또는 ADMIN)
 * 인지 검증한다 — roomId만 알면 타인의 상담 내용을 도청할 수 있기 때문.
 * 여기서 던진 예외는 STOMP ERROR 프레임으로 클라이언트에 전달되고 연결/구독이 거부된다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_DESTINATION_PREFIX = "/sub/chat/room/";

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomService chatRoomService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(token);
        String role = jwtTokenProvider.getRole(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long roomId = parseRoomId(accessor.getDestination());
        chatRoomService.validateSubscription(accessor.getUser().getName(), roomId);
    }

    /** "/sub/chat/room/{roomId}" 외의 목적지는 구독을 허용하지 않는다. */
    private Long parseRoomId(String destination) {
        if (destination == null || !destination.startsWith(CHAT_DESTINATION_PREFIX)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        try {
            return Long.parseLong(destination.substring(CHAT_DESTINATION_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
