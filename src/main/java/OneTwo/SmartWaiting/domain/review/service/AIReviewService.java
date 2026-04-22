package OneTwo.SmartWaiting.domain.review.service;

import OneTwo.SmartWaiting.domain.review.entity.Review;
import OneTwo.SmartWaiting.domain.review.repository.ReviewRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIReviewService {

    private final ChatClient chatClient;
    private final ReviewRepository reviewRepository;

    public AIReviewService(ChatClient.Builder builder, ReviewRepository reviewRepository) {
        this.chatClient = builder.build();
        this.reviewRepository = reviewRepository;
    }

    // "aiSummary"라는 이름으로 storeId마다 결과를 Redis에 저장
    @Cacheable(value = "aiSummary", key = "#storeId")
    @Transactional(readOnly = true)
    public String getAIReviewSummary(Long storeId) {
        // 1. 데이터 수집: 해당 식당의 최근 리뷰 최대 50개를 가져옴
        List<Review> recentReviews = reviewRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId);

        if (recentReviews.isEmpty()) {
            return "아직 요약할 리뷰가 부족합니다.";
        }

        // 2. 데이터 가공: 리뷰 내용들만 쭉 이어 붙여서 하나의 텍스트로 만듬
        String aggregatedReviews = recentReviews.stream()
                .map(Review::getContent)
                .collect(Collectors.joining("\n- "));

        // 3. 프롬프트 작성
        String prompt = String.format(
                "너는 맛집 리뷰 전문 분석가야. 다음은 특정 식당의 실제 방문자 리뷰들이야.\n\n" +
                        "[리뷰 원본]\n%s\n\n" +
                        "[지시사항]\n" +
                        "위 리뷰들을 종합해서 다음 규칙에 따라 정확히 3줄로 요약해줘:\n" +
                        "1. 맛, 분위기, 서비스 관점에서 각각 1줄씩 작성할 것\n" +
                        "2. 각 줄은 '✔️' 기호로 시작할 것\n" +
                        "3. 존댓말을 사용하고, 핵심만 간결하게 말할 것",
                aggregatedReviews
        );

        // 4. LLM 호출 및 결과 반환
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}