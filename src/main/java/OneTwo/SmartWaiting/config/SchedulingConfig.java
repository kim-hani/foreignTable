package OneTwo.SmartWaiting.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// @Scheduled(리뷰 요청 FCM, 새벽 정리 배치, SSE Heartbeat)가 동작하려면 반드시 필요
// STOMP 브로커의 messageBrokerTaskScheduler가 TaskScheduler 빈으로 등록되어 있어, 지정하지 않으면 @Scheduled가
// 그 스케줄러를 빌려 쓰고 spring.task.scheduling.* 설정도 적용되지 않는다 → 전용 스케줄러를 명시적으로 지정
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig implements SchedulingConfigurer, DisposableBean {

    // spring.task.scheduling.pool.size / thread-name-prefix 설정이 반영된 빌더
    private final ThreadPoolTaskSchedulerBuilder schedulerBuilder;

    private ThreadPoolTaskScheduler scheduler;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        scheduler = schedulerBuilder.build();
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
