package OneTwo.SmartWaiting.domain.review.controller;

import OneTwo.SmartWaiting.domain.review.dto.requestDto.ReviewCreateRequestDto;
import OneTwo.SmartWaiting.domain.review.dto.responseDto.ReviewResponseDto;
import OneTwo.SmartWaiting.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 작성
    @PostMapping
    public ResponseEntity<Long> createReview(@RequestBody @Valid ReviewCreateRequestDto request) {
        Long reviewId = reviewService.createReview(request);
        return ResponseEntity.created(URI.create("/api/v1/reviews/" + reviewId)).body(reviewId);
    }

    // 조회 (특정 가게의 리뷰)
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ReviewResponseDto>> getStoreReviews(@PathVariable Long storeId) {
        return ResponseEntity.ok(reviewService.getStoreReviews(storeId));
    }

    // 삭제
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId,
            @RequestParam Long memberId) { // 로그인 구현 전이라 param으로 받음
        reviewService.deleteReview(reviewId, memberId);
        return ResponseEntity.noContent().build();
    }
}