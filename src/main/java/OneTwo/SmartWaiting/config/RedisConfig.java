package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.domain.chat.redis.RedisChatSubscriber;
import OneTwo.SmartWaiting.domain.notification.redis.SseRedisPublisher;
import OneTwo.SmartWaiting.domain.notification.redis.SseRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 실시간 상담 채팅(#43)용 Redis Pub/Sub 설정.
 *
 * <p>방마다 채널을 동적으로 구독/해제하는 대신 패턴("chat:room:*") 하나만 등록해
 * 모든 방의 이벤트를 받는다 — 채널명에서 roomId를 파싱해 브로커 목적지로 라우팅.
 * RedisConnectionFactory/StringRedisTemplate은 spring-boot-starter-data-redis
 * 자동구성을 사용한다 (Redisson은 분산락 전용으로 별도 유지 — RedissonConfig).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatSubscriber redisChatSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisChatSubscriber, new PatternTopic("chat:room:*"));
        return container;
    }

    /**
     * SSE 스케일 아웃용 Redis Pub/Sub ("sse:notification").
     *
     * <p>채팅과 컨테이너를 분리해 단일 스레드로 처리한다. 기본 컨테이너는 메시지마다 다른 스레드에서 처리할 수 있어
     * "안내 메시지 → 연결 종료"처럼 순서가 중요한 요청이 뒤바뀌면 메시지가 유실된다.
     */
    @Bean
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseRedisSubscriber sseRedisSubscriber
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("sse-redis-");
        executor.setDaemon(true);
        executor.initialize();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(executor);
        container.addMessageListener(sseRedisSubscriber, new ChannelTopic(SseRedisPublisher.CHANNEL));
        return container;
    }
}
