package OneTwo.SmartWaiting.domain.review.controller;

import OneTwo.SmartWaiting.domain.review.dto.requestDto.ReviewCreateRequestDto;
import OneTwo.SmartWaiting.domain.review.dto.responseDto.ReviewResponseDto;
import OneTwo.SmartWaiting.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(name = "5. 리뷰(Review) API", description = "식당 리뷰 작성, 조회, 삭제 기능")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 작성
    @Operation(summary = "리뷰 작성", description = "특정 식당에 대한 리뷰와 별점을 등록합니다.")
    @PostMapping
    public ResponseEntity<Long> createReview(
            Principal principal,
            @RequestBody @Valid ReviewCreateRequestDto request) {
        Long reviewId = reviewService.createReview(request,principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/reviews/" + reviewId)).body(reviewId);
    }

    // 조회 (특정 가게의 리뷰)
    @Operation(summary = "가게 리뷰 전체 조회", description = "특정 식당에 달린 모든 리뷰를 최신순으로 조회합니다.")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ReviewResponseDto>> getStoreReviews(@PathVariable Long storeId) {
        return ResponseEntity.ok(reviewService.getStoreReviews(storeId));
    }

    // 삭제
    @Operation(summary = "리뷰 삭제", description = "본인이 작성한 리뷰를 삭제합니다.")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId,
            Principal principal) {
        reviewService.deleteReview(reviewId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}