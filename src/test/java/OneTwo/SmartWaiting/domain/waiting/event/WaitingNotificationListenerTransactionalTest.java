package OneTwo.SmartWaiting.domain.waiting.event;

import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 웨이팅 알림이 진짜 "커밋된 다음에만" 나가는지 확인하는 테스트.
 *
 * 단위 테스트(WaitingNotificationListenerTest)는 리스너 메서드를 직접 부르므로 @TransactionalEventListener
 * 배선 자체는 검증하지 못한다. H2로 진짜 트랜잭션을 열어 커밋/롤백을 태워 본다 (SupportManualIndexerTransactionalTest와 같은 방식).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WaitingNotificationListenerTransactionalTest.TestConfig.class)
class WaitingNotificationListenerTransactionalTest {

    @EnableTransactionManagement
    @Configuration
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            // 스키마는 필요 없다. 커밋/롤백을 걸 진짜 트랜잭션 자원만 있으면 된다.
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        WaitingRepository waitingRepository() {
            return mock(WaitingRepository.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        FcmService fcmService() {
            return mock(FcmService.class);
        }

        @Bean
        WaitingNotificationListener waitingNotificationListener(WaitingRepository waitingRepository,
                                                                NotificationService notificationService,
                                                                FcmService fcmService) {
            return new WaitingNotificationListener(waitingRepository, notificationService, fcmService);
        }
    }

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private NotificationService notificationService;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // 목이 컨텍스트 안에서 공유되므로 테스트마다 초기화
        reset(notificationService);
    }

    @Test
    @DisplayName("커밋 전에는 보내지 않고, 커밋된 뒤 발행 순서대로(메시지 → 연결 종료) 보낸다")
    void sendsOnlyAfterCommit_InPublishOrder() {
        // when
        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new WaitingMessageEvent(100L, "자동 취소 안내"));
            eventPublisher.publishEvent(new WaitingConnectionCloseEvent(100L));

            // 트랜잭션 안(커밋 전)에서는 아직 아무것도 나가지 않아야 함
            verify(notificationService, never()).sendToClient(anyLong(), anyString());
            verify(notificationService, never()).closeConnection(anyLong());
        });

        // then
        InOrder inOrder = inOrder(notificationService);
        inOrder.verify(notificationService).sendToClient(100L, "자동 취소 안내");
        inOrder.verify(notificationService).closeConnection(100L);
    }

    @Test
    @DisplayName("롤백되면 알림을 보내지 않는다")
    void doesNotSendOnRollback() {
        // when
        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new WaitingMessageEvent(100L, "사장님 호출"));
            status.setRollbackOnly();
        });

        // then
        verify(notificationService, never()).sendToClient(anyLong(), anyString());
    }
}
