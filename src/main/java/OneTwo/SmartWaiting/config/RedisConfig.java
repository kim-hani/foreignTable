package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.domain.chat.redis.RedisChatSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

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
}
