package OneTwo.SmartWaiting.domain.support.dto;

/**
 * AI 챗봇 응답(#41).
 *
 * @param answer     매뉴얼에 근거한 AI 답변, 또는 상담원 연결 안내 문구
 * @param needsAgent 매뉴얼로 답할 수 없어 상담원 연결이 필요한지 여부 (프론트가 '상담원 연결' 버튼 노출 등에 활용)
 */
public record SupportChatResponseDto(
        String answer,
        boolean needsAgent
) {
    public static SupportChatResponseDto answered(String answer) {
        return new SupportChatResponseDto(answer, false);
    }

    public static SupportChatResponseDto escalate(String answer) {
        return new SupportChatResponseDto(answer, true);
    }
}
