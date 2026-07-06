package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatResponseDto;
import OneTwo.SmartWaiting.domain.support.rag.SupportManualDocumentMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 고객지원 챗봇 서비스 (RAG 기반).
 *
 * <p>#41은 전체 매뉴얼을 통째로 프롬프트에 주입했지만, #42부터는 질문과 의미적으로 유사한
 * 매뉴얼 top-k만 벡터 유사도 검색으로 골라 주입한다(retrieve → augment → generate).
 * "매뉴얼에 근거해서만 답하라"는 그라운딩 지시로 환각을 억제하고,
 * 답할 수 없으면 상담원 연결을 안내한다.
 *
 * <p>관용적 대안: Spring AI의 QuestionAnswerAdvisor를 붙이면 retrieve+augment가 자동화되지만,
 * 유사도 임계값/카테고리 필터/NO_ANSWER 분기를 세밀 제어하기 위해 similaritySearch를 직접 호출한다.
 */
@Service
public class SupportChatService {

    /** 검색해 프롬프트에 넣을 매뉴얼 최대 개수. */
    private static final int TOP_K = 4;
    /** 이 유사도 미만인 문서는 무관하다고 보고 제외 (0.0~1.0). */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    /** 매뉴얼로 답할 수 없을 때 AI가 반환하도록 지시하는 신호값. */
    private static final String NO_ANSWER = "[[NO_ANSWER]]";

    /** 상담원 연결 안내 문구. */
    private static final String ESCALATE_MESSAGE =
            "죄송합니다. 문의하신 내용은 매뉴얼로 안내드리기 어려워 상담원 연결이 필요합니다.";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public SupportChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    public SupportChatResponseDto chat(SupportChatRequestDto request) {
        // ── retrieve: 질문을 임베딩해 유사한 매뉴얼 top-k 검색 ─────────────
        List<Document> hits = retrieve(request);

        // 관련 매뉴얼이 없으면 AI 호출 없이 즉시 상담원 안내
        if (hits.isEmpty()) {
            return SupportChatResponseDto.escalate(ESCALATE_MESSAGE);
        }

        // ── augment: 검색된 문서만 프롬프트에 주입(그라운딩) ─────────────
        String knowledge = hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        String prompt = buildGroundedPrompt(knowledge, request.question());

        // ── generate: LLM 호출 ─────────────────────────────────────────
        String answer = chatClient.prompt().user(prompt).call().content();

        // 매뉴얼로 답할 수 없다는 신호면 상담원 연결로 분기
        if (answer == null || answer.contains(NO_ANSWER)) {
            return SupportChatResponseDto.escalate(ESCALATE_MESSAGE);
        }

        return SupportChatResponseDto.answered(answer.trim());
    }

    /** 벡터 유사도 검색으로 질문과 가까운 매뉴얼 문서를 가져온다. */
    private List<Document> retrieve(SupportChatRequestDto request) {
        SearchRequest.Builder search = SearchRequest.builder()
                .query(request.question())
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD);

        // 카테고리 지정 시 해당 카테고리 문서로 검색 범위 제한
        if (request.category() != null && !request.category().isBlank()) {
            search.filterExpression(new FilterExpressionBuilder()
                    .eq(SupportManualDocumentMapper.META_CATEGORY, request.category())
                    .build());
        }

        try {
            return vectorStore.similaritySearch(search.build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SUPPORT_EMBEDDING_FAILED);
        }
    }

    /** 검색된 매뉴얼에만 근거해 답하도록 지시하는 그라운딩 프롬프트. */
    private String buildGroundedPrompt(String knowledge, String question) {
        return String.format(
                "너는 SmartWaiting 서비스의 고객지원 상담원이야. 아래 [고객지원 매뉴얼]에 근거해서만 답변해야 해.\n\n" +
                        "[고객지원 매뉴얼]\n%s\n\n" +
                        "[답변 규칙]\n" +
                        "1. 반드시 위 매뉴얼 내용에 근거해서만 답할 것. 매뉴얼에 없는 내용은 절대 지어내지 말 것.\n" +
                        "2. 매뉴얼로 답할 수 없는 질문이면 다른 말 없이 정확히 '%s' 라고만 답할 것.\n" +
                        "3. 존댓말로, 핵심만 간결하게 답할 것.\n\n" +
                        "[고객 질문]\n%s",
                knowledge, NO_ANSWER, question
        );
    }
}
