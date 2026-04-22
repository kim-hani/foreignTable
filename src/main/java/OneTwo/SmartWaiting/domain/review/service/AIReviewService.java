package OneTwo.SmartWaiting.domain.review.service;

import OneTwo.SmartWaiting.domain.review.entity.Review;
import OneTwo.SmartWaiting.domain.review.repository.ReviewRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIReviewService {

    private final ChatClient chatClient;
    private final ReviewRepository reviewRepository;
    private final StringRedisTemplate redisTemplate;

    public AIReviewService(ChatClient.Builder builder, ReviewRepository reviewRepository, StringRedisTemplate redisTemplate) {
        this.chatClient = builder.build();
        this.reviewRepository = reviewRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public String getAIReviewSummary(Long storeId) {
        String cacheKey = "aiSummary::" + storeId;

        // 1. 캐시 히트 (Cache Hit): Redis에 요약본이 살아있다면 바로 반환 (AI 호출 안 함)
        String cachedSummary = redisTemplate.opsForValue().get(cacheKey);
        if (cachedSummary != null) {
            return cachedSummary;
        }

        // 2. 리뷰 개수 확인
        long totalReviewCount = reviewRepository.countByStoreId(storeId);

        // 3. 조건 1: 50개 미만이면 요약 안 함
        if (totalReviewCount < 50) {
            return "리뷰가 50개 이상 모이면 AI 요약이 제공됩니다.";
        }

        // 4. 데이터 수집: 최신 리뷰 50개 가져오기
        List<Review> recentReviews = reviewRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId);
        String aggregatedReviews = recentReviews.stream()
                .map(Review::getContent)
                .collect(Collectors.joining("\n- "));

        // 5. AI 프롬프트 작성 및 호출 (Cache Miss 상황)
        String prompt = String.format(
                "너는 맛집 리뷰 전문 분석가야. 다음은 특정 식당의 실제 방문자 리뷰들이야.\n\n" +
                        "[리뷰 원본]\n%s\n\n" +
                        "위 리뷰들을 종합해서 다음 규칙에 따라 정확히 3줄로 요약해줘:\n" +
                        "1. 맛, 분위기, 서비스 관점에서 각각 1줄씩 작성할 것\n" +
                        "2. 각 줄은 '✔️' 기호로 시작할 것\n" +
                        "3. 존댓말을 사용하고, 핵심만 간결하게 말할 것",
                aggregatedReviews
        );

        String summary = chatClient.prompt().user(prompt).call().content();

        // 6. 동적 TTL 전략 적용 (리뷰 개수에 따라 캐시 수명 결정)
        long ttlDays;
        if (totalReviewCount >= 500) {
            ttlDays = 3;
        } else if (totalReviewCount >= 300) {
            ttlDays = 5;
        } else if (totalReviewCount >= 100) {
            ttlDays = 7;
        } else { // 50개 이상 100개 미만
            ttlDays = 10;
        }

        // 7. Redis에 결과 저장
        redisTemplate.opsForValue().set(cacheKey, summary, Duration.ofDays(ttlDays));

        return summary;
    }
}