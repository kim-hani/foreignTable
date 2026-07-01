package OneTwo.SmartWaiting.domain.support.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 챗봇 질문 요청(#41). 인증된 사용자가 고객지원 챗봇에 질문한다.
 */
public record SupportChatRequestDto(

        @NotBlank(message = "질문은 필수입니다.")
        String question,

        String category   // 선택: 특정 카테고리 매뉴얼만 근거로 삼고 싶을 때
) {
}
