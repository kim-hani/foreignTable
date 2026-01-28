package OneTwo.SmartWaiting.domain.review.service;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.review.dto.requestDto.ReviewCreateRequestDto;
import OneTwo.SmartWaiting.domain.review.dto.responseDto.ReviewResponseDto;
import OneTwo.SmartWaiting.domain.review.entity.Review;
import OneTwo.SmartWaiting.domain.review.repository.ReviewRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;

    // 1. 리뷰 작성
    @Transactional
    public Long createReview(ReviewCreateRequestDto request) {
        Store store = storeRepository.findById(request.storeId()) // request.storeId() 사용!
                .orElseThrow(() -> new IllegalArgumentException("가게가 존재하지 않습니다."));
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        // TODO: 나중에는 '실제 방문한 사람만' 작성 가능하도록 검증 로직 추가 필요

        Review review = Review.builder()
                .store(store)
                .member(member)
                .content(request.content())
                .rating(request.rating())
                .build();

        return reviewRepository.save(review).getId();
    }

    // 2. 가게별 리뷰 조회
    public List<ReviewResponseDto> getStoreReviews(Long storeId) {
        return reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(ReviewResponseDto::from)
                .collect(Collectors.toList());
    }

    // 3. 리뷰 삭제 (작성자 본인 확인 필요 - 일단은 ID만 받아서 삭제)
    @Transactional
    public void deleteReview(Long reviewId, Long memberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않습니다."));

        if (!review.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 리뷰만 삭제할 수 있습니다.");
        }

        reviewRepository.delete(review);
    }
}