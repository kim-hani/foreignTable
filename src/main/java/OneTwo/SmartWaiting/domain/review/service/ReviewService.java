package OneTwo.SmartWaiting.domain.review.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.review.dto.requestDto.ReviewCreateRequestDto;
import OneTwo.SmartWaiting.domain.review.dto.responseDto.ReviewResponseDto;
import OneTwo.SmartWaiting.domain.review.entity.Review;
import OneTwo.SmartWaiting.domain.review.repository.ReviewRepository;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final MemberRepository memberRepository;
    private final WaitingRepository waitingRepository;

    // 1. 리뷰 작성
    @Transactional
    public Long createReview(ReviewCreateRequestDto request, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Waiting waiting = waitingRepository.findById(request.waitingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        if (!waiting.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);
        }

        if (waiting.getStatus() != WaitingStatus.SEATED) {
            throw new BusinessException(ErrorCode.REVIEW_UNAUTHORIZED_VISIT);
        }

        if (waiting.getUpdatedAt().plusHours(48).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REVIEW_TIME_EXPIRED);
        }

        if(reviewRepository.existsByWaitingId(waiting.getId())){
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .store(waiting.getStore())
                .member(member)
                .waiting(waiting)
                .content(request.content())
                .rating(request.rating())
                .build();

        return reviewRepository.save(review).getId();
    }

    // 2. 가게별 리뷰 조회
    public Slice<ReviewResponseDto> getStoreReviews(Long storeId, Pageable pageable) {
        return reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId,pageable)
                .map(ReviewResponseDto::from);
    }

    // 3. 리뷰 삭제 (작성자 본인 확인 필요 - 일단은 ID만 받아서 삭제)
    @Transactional
    public void deleteReview(Long reviewId, String email) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        Member requester = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!review.getMember().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_REVIEW);
        }

        reviewRepository.delete(review);
    }

    // 4. 내 리뷰 목록 조회
    public Slice<ReviewResponseDto> getMyReviews(String email,Pageable pageable) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return reviewRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId(),pageable)
                .map(ReviewResponseDto::from);
    }
}