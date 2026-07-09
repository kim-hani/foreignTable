package OneTwo.SmartWaiting.domain.support.rag;

import OneTwo.SmartWaiting.domain.support.event.SupportManualIndexEvent;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 색인기가 진짜 "커밋된 다음에만" 도는지 확인하는 테스트.
 *
 * 단위 테스트(SupportManualIndexerTest)는 sync()를 직접 부르니까 @TransactionalEventListener
 * 배선 자체는 검증이 안 된다. 누가 실수로 phase를 바꾸거나 서비스의 @Transactional을 떼도 못 잡는다.
 * 그래서 여기선 H2로 진짜 트랜잭션을 열어 커밋/롤백을 태워본다. repository·VectorStore는 목이라 DB엔
 * 아무것도 안 넣고, 매뉴얼은 없는 것으로 두면 sync는 벡터 제거(delete)만 부른다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SupportManualIndexerTransactionalTest.TestConfig.class)
class SupportManualIndexerTransactionalTest {

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
        SupportManualRepository supportManualRepository() {
            return mock(SupportManualRepository.class);
        }

        @Bean
        VectorStore vectorStore() {
            return mock(VectorStore.class);
        }

        @Bean
        SupportManualIndexer supportManualIndexer(SupportManualRepository repository, VectorStore vectorStore) {
            return new SupportManualIndexer(repository, vectorStore);
        }
    }

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private SupportManualRepository supportManualRepository;
    @Autowired
    private VectorStore vectorStore;

    private TransactionTemplate txTemplate;

    private static final Long MANUAL_ID = 1L;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // 목이 컨텍스트 안에서 공유되므로 테스트마다 초기화. 매뉴얼은 없는 상태(삭제 경로)로 둔다.
        reset(supportManualRepository, vectorStore);
        when(supportManualRepository.findByIdAndIsDeletedFalse(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("커밋되면 색인기가 이벤트를 받아 벡터를 처리한다")
    void firesAfterCommit() {
        // when — 트랜잭션 안에서 이벤트를 던지고 정상 커밋
        txTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(SupportManualIndexEvent.of(MANUAL_ID)));

        // then
        verify(vectorStore, times(1)).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("롤백되면 색인기가 아예 돌지 않는다 (유령 벡터 방지)")
    void doesNotFireOnRollback() {
        // when — 이벤트를 던진 뒤 롤백
        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(SupportManualIndexEvent.of(MANUAL_ID));
            status.setRollbackOnly();
        });

        // then — AFTER_COMMIT이라 롤백 시엔 리스너가 안 탄다
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }
}
