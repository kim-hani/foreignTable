package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.domain.support.dto.SupportChatRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatResponseDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

    @Mock
    private SupportManualRepository supportManualRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private SupportChatService supportChatService;

    @BeforeEach
    void setUp() {
        // 서비스가 생성자에서 builder.build()를 호출하므로 목 조립 후 직접 생성
        when(chatClientBuilder.build()).thenReturn(chatClient);
        supportChatService = new SupportChatService(chatClientBuilder, supportManualRepository);
    }

    private SupportManual manual(String category) {
        return SupportManual.builder()
                .category(category).question("Q").answer("A").keywords("k").build();
    }

    private void stubAiAnswer(String answer) {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(answer);
    }

    // ================= [ 정상 응답 ] =================

    @Test
    @DisplayName("챗봇 응답 성공 - 매뉴얼 근거로 답변하며 needsAgent=false 이다.")
    void chat_Success_Answered() {
        // given
        when(supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(manual("환불")));
        stubAiAnswer("마이페이지에서 환불 신청이 가능합니다.");
        SupportChatRequestDto request = new SupportChatRequestDto("환불은 어떻게 하나요?", null);

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isFalse();
        assertThat(result.answer()).isEqualTo("마이페이지에서 환불 신청이 가능합니다.");
    }

    @Test
    @DisplayName("카테고리 지정 시 해당 카테고리 매뉴얼만 조회한다.")
    void chat_Success_FilterByCategory() {
        // given
        when(supportManualRepository.findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc("웨이팅"))
                .thenReturn(List.of(manual("웨이팅")));
        stubAiAnswer("웨이팅은 앱에서 취소할 수 있습니다.");
        SupportChatRequestDto request = new SupportChatRequestDto("웨이팅 취소는?", "웨이팅");

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isFalse();
        verify(supportManualRepository, times(1))
                .findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc("웨이팅");
        verify(supportManualRepository, never()).findAllByIsDeletedFalseOrderByCreatedAtDesc();
    }

    // ================= [ 상담원 연결 분기 ] =================

    @Test
    @DisplayName("매뉴얼이 없으면 AI 호출 없이 상담원 연결(needsAgent=true)을 안내한다.")
    void chat_Escalate_WhenNoManual() {
        // given
        when(supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of());
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
        when(supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(manual("환불")));
        stubAiAnswer("[[NO_ANSWER]]");
        SupportChatRequestDto request = new SupportChatRequestDto("매뉴얼에 없는 질문", null);

        // when
        SupportChatResponseDto result = supportChatService.chat(request);

        // then
        assertThat(result.needsAgent()).isTrue();
    }
}
