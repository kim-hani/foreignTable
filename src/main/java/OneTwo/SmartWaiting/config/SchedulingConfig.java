package OneTwo.SmartWaiting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled(리뷰 요청 FCM, 새벽 정리 배치, SSE Heartbeat)가 동작하려면 반드시 필요
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
