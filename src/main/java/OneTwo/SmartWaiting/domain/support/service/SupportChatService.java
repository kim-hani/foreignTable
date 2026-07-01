package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.domain.support.dto.SupportChatRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatResponseDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 고객지원 챗봇 서비스.
 *
 * 운영자(ADMIN)가 등록한 {@link SupportManual}을 지식 출처로 삼아,
 * "매뉴얼에 근거해서만 답하라"는 그라운딩 지시로 환각을 억제한다.
 * 매뉴얼로 답할 수 없으면 상담원 연결을 안내한다.
 */
@Service
public class SupportChatService {

    /** 매뉴얼로 답할 수 없을 때 AI가 반환하도록 지시하는 신호값. */
    private static final String NO_ANSWER = "[[NO_ANSWER]]";

    /** 상담원 연결 안내 문구. */
    private static final String ESCALATE_MESSAGE =
            "죄송합니다. 문의하신 내용은 매뉴얼로 안내드리기 어려워 상담원 연결이 필요합니다.";

    private final ChatClient chatClient;
    private final SupportManualRepository supportManualRepository;

    public SupportChatService(ChatClient.Builder builder, SupportManualRepository supportManualRepository) {
        this.chatClient = builder.build();
        this.supportManualRepository = supportManualRepository;
    }

    @Transactional(readOnly = true)
    public SupportChatResponseDto chat(SupportChatRequestDto request) {
        // 1. 지식 출처(매뉴얼) 수집 — 카테고리 지정 시 해당 카테고리만
        List<SupportManual> manuals = (request.category() == null || request.category().isBlank())
                ? supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()
                : supportManualRepository.findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(request.category());

        // 2. 매뉴얼이 하나도 없으면 AI 호출 없이 즉시 상담원 안내
        if (manuals.isEmpty()) {
            return SupportChatResponseDto.escalate(ESCALATE_MESSAGE);
        }

        // 3. 매뉴얼을 프롬프트에 주입할 지식 블록으로 조립
        String knowledge = manuals.stream()
                .map(m -> String.format("[%s] Q: %s\nA: %s", m.getCategory(), m.getQuestion(), m.getAnswer()))
                .collect(Collectors.joining("\n\n"));

        // 4. 그라운딩 프롬프트 작성 (매뉴얼 밖 질문은 NO_ANSWER 신호 반환하도록 지시)
        String prompt = String.format(
                "너는 SmartWaiting 서비스의 고객지원 상담원이야. 아래 [고객지원 매뉴얼]에 근거해서만 답변해야 해.\n\n" +
                        "[고객지원 매뉴얼]\n%s\n\n" +
                        "[답변 규칙]\n" +
                        "1. 반드시 위 매뉴얼 내용에 근거해서만 답할 것. 매뉴얼에 없는 내용은 절대 지어내지 말 것.\n" +
                        "2. 매뉴얼로 답할 수 없는 질문이면 다른 말 없이 정확히 '%s' 라고만 답할 것.\n" +
                        "3. 존댓말로, 핵심만 간결하게 답할 것.\n\n" +
                        "[고객 질문]\n%s",
                knowledge, NO_ANSWER, request.question()
        );

        // 5. AI 호출
        String answer = chatClient.prompt().user(prompt).call().content();

        // 6. 매뉴얼로 답할 수 없다는 신호면 상담원 연결로 분기
        if (answer == null || answer.contains(NO_ANSWER)) {
            return SupportChatResponseDto.escalate(ESCALATE_MESSAGE);
        }

        return SupportChatResponseDto.answered(answer.trim());
    }
}
