package OneTwo.SmartWaiting.domain.support.rag;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.event.SupportManualIndexEvent;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportManualIndexerTest {

    @InjectMocks
    private SupportManualIndexer supportManualIndexer;

    @Mock
    private SupportManualRepository supportManualRepository;

    @Mock
    private VectorStore vectorStore;

    private static final Long MANUAL_ID = 1L;

    private SupportManual mockManual() {
        SupportManual manual = mock(SupportManual.class);
        when(manual.getId()).thenReturn(MANUAL_ID);
        when(manual.getCategory()).thenReturn("환불");
        when(manual.getQuestion()).thenReturn("Q");
        when(manual.getAnswer()).thenReturn("A");
        when(manual.getKeywords()).thenReturn("k");
        return manual;
    }

    @Test
    @DisplayName("살아있는 매뉴얼 - DB 최신값을 읽어 기존 벡터 제거 후 재색인한다.")
    void sync_Existing_ReindexesInOrder() {
        // given
        SupportManual manual = mockManual();
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.of(manual));

        // when
        supportManualIndexer.sync(MANUAL_ID);

        // then — 삭제 후 추가 순서 보장
        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(any(Filter.Expression.class));
        inOrder.verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("지워졌거나 없는 매뉴얼 - 벡터를 제거만 하고 add는 호출하지 않는다.")
    void sync_Missing_RemovesOnly() {
        // given
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());

        // when
        supportManualIndexer.sync(MANUAL_ID);

        // then
        verify(vectorStore, times(1)).delete(any(Filter.Expression.class));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    @DisplayName("색인이 한 번 실패해도 바로 재시도해 결국 성공한다.")
    void sync_RetriesThenSucceeds() {
        // given — 첫 시도는 실패, 두 번째는 성공
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("embedding API glitch"))
                .doNothing()
                .when(vectorStore).delete(any(Filter.Expression.class));

        // when & then
        assertDoesNotThrow(() -> supportManualIndexer.sync(MANUAL_ID));
        verify(vectorStore, times(2)).delete(any(Filter.Expression.class));   // 1회 실패 + 1회 성공
    }

    @Test
    @DisplayName("색인이 계속 실패하면 최대 횟수까지 시도하고 예외는 삼킨다.")
    void sync_AllAttemptsFail_Swallowed() {
        // given — 매 시도 실패
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("embedding API down"))
                .when(vectorStore).delete(any(Filter.Expression.class));

        // when & then — 밖으로 예외가 새어나가지 않아야 한다
        assertDoesNotThrow(() -> supportManualIndexer.sync(MANUAL_ID));
        verify(vectorStore, times(3)).delete(any(Filter.Expression.class));   // MAX_ATTEMPTS
    }

    @Test
    @DisplayName("이벤트를 받으면 해당 manualId를 sync한다.")
    void handle_DelegatesToSync() {
        // given
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());

        // when
        supportManualIndexer.handle(SupportManualIndexEvent.of(MANUAL_ID));

        // then
        verify(supportManualRepository, times(1)).findByIdAndIsDeletedFalse(MANUAL_ID);
        verify(vectorStore, times(1)).delete(any(Filter.Expression.class));
    }
}
