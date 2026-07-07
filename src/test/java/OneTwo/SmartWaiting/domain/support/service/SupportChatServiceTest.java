package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.domain.support.dto.SupportChatRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private SupportChatService supportChatService;

    @BeforeEach
    void setUp() {
        // 서비스가 생성자에서 builder.build()를 호출하므로 목 조립 후 직접 생성
        when(chatClientBuilder.build()).thenReturn(chatClient);
        supportChatService = new SupportChatService(chatClientBuilder, vectorStore);
    }

    private Document manualDoc(String category) {
        return Document.builder()
                .text(String.format("[%s] Q: Q%nA: A%nkeywords: k", category))
                .metadata(Map.of("manualId", 1L, "category", category))
                .build();
    }

    private void stubSearchReturns(List<Document> hits) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(hits);
    }

    private void stubAiAnswer(String answer) {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(answer);
    }

    // ================= [ 정상 응답 ] =================

    @Test
    @DisplayName("챗봇 응답 성공 - 검색된 매뉴얼 근거로 답변하며 needsAgent=false 이다.")
    void chat_Success_Answered() {
        // given
        stubSearchReturns(List.of(manualDoc("환불")));
        stubAiAnswer("마이페이지에서 환불 신청이 가능합니다.");
        SupportChatRequestDto request = new SupportChatRequestDto("환불은 어떻게 하나요?", null);

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isFalse();
        assertThat(result.answer()).isEqualTo("마이페이지에서 환불 신청이 가능합니다.");
    }

    @Test
    @DisplayName("카테고리 지정 시 유사도 검색에 카테고리 필터가 적용된다.")
    void chat_Success_FilterByCategory() {
        // given
        stubSearchReturns(List.of(manualDoc("웨이팅")));
        stubAiAnswer("웨이팅은 앱에서 취소할 수 있습니다.");
        SupportChatRequestDto request = new SupportChatRequestDto("웨이팅 취소는?", "웨이팅");

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isFalse();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        // 카테고리를 지정했으므로 검색 요청에 metadata 필터가 실려야 함
        assertThat(captor.getValue().getFilterExpression()).isNotNull();
    }

    @Test
    @DisplayName("카테고리 미지정 시 유사도 검색에 필터가 없다.")
    void chat_Success_NoFilterWhenCategoryNull() {
        // given
        stubSearchReturns(List.of(manualDoc("환불")));
        stubAiAnswer("답변");
        SupportChatRequestDto request = new SupportChatRequestDto("질문", null);

        // when
        supportChatService.chat(request);

        // then
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNull();
    }

    // ================= [ 상담원 연결 분기 ] =================

    @Test
    @DisplayName("유사한 매뉴얼이 없으면 AI 호출 없이 상담원 연결(needsAgent=true)을 안내한다.")
    void chat_Escalate_WhenNoHit() {
        // given
        stubSearchReturns(List.of());
        SupportChatRequestDto request = new SupportChatRequestDto("아무 질문", null);

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isTrue();
        verify(chatClient, never()).prompt();   // AI 호출이 일어나지 않아야 함
    }

    @Test
    @DisplayName("AI가 NO_ANSWER 신호를 반환하면 상담원 연결(needsAgent=true)로 분기한다.")
    void chat_Escalate_WhenAiCannotAnswer() {
        // given
        stubSearchReturns(List.of(manualDoc("환불")));
        stubAiAnswer("[[NO_ANSWER]]");
        SupportChatRequestDto request = new SupportChatRequestDto("매뉴얼에 없는 질문", null);

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isTrue();
    }
}
