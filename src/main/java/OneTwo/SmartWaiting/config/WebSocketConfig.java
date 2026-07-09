package OneTwo.SmartWaiting.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 상담 채팅(#43)용 WebSocket(STOMP) 설정.
 *
 * <p>클라이언트는 {@code ws://host/ws}로 접속한 뒤,
 * {@code /pub/**}로 메시지를 보내고(@MessageMapping 라우팅)
 * {@code /sub/**}를 구독해 브로드캐스트를 받는다.
 *
 * <p>브로커는 Spring 내장 SimpleBroker(인메모리)를 쓰되, 다중 인스턴스 간 전달은
 * Redis Pub/Sub 릴레이(RedisChatPublisher/Subscriber)가 담당한다 — 인스턴스마다
 * 브로커가 따로 있어도 모든 인스턴스가 같은 Redis 채널을 구독하므로 메시지가 전파된다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebConfig의 REST CORS와 동일 출처만 허용 (127.0.0.1은 IPv6 localhost 이슈 대비 로컬 개발용)
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:3000", "http://127.0.0.1:3000");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");                    // 구독(브로커 → 클라이언트)
        registry.setApplicationDestinationPrefixes("/pub");     // 발행(클라이언트 → @MessageMapping)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 시 JWT 인증, SUBSCRIBE 시 방 참여자 검증
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
