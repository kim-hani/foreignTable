package OneTwo.SmartWaiting.domain.review.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.review.dto.requestDto.ReviewCreateRequestDto;
import OneTwo.SmartWaiting.domain.review.dto.responseDto.ReviewResponseDto;
import OneTwo.SmartWaiting.domain.review.entity.Review;
import OneTwo.SmartWaiting.domain.review.repository.ReviewRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WaitingRepository waitingRepository;

    // ================= [ 리뷰 작성 (Create) ] =================

    @Test
    @DisplayName("리뷰 작성 성공")
    void createReview_Success() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(waitingId, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(100L, mockMember, mockStore, mockWaiting, requestDto.content(), requestDto.rating());

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(reviewRepository.existsByWaitingId(waitingId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(mockReview);

        // when
        Long resultId = reviewService.createReview(requestDto, email);

        // then
        assertThat(resultId).isEqualTo(100L);
        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 존재하지 않는 회원")
    void createReview_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@test.com";
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(1L, "맛있어요!", 5);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 존재하지 않는 웨이팅 내역")
    void createReview_Fail_WaitingNotFound() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.findById(waitingId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_NOT_FOUND);
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 본인의 웨이팅 내역이 아님")
    void createReview_Fail_NotYourWaiting() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Member otherMember = createMockMember(2L, "other@test.com", "다른유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(waitingId, otherMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_YOUR_WAITING);
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 웨이팅 상태가 착석(SEATED)이 아님")
    void createReview_Fail_WaitingStatusNotSeated() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(waitingId, mockMember, mockStore, WaitingStatus.WAITING, LocalDateTime.now()); // SEATED가 아님

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        lenient().when(mockWaiting.getMember()).thenReturn(mockMember); // UnnecessaryStubbingException 방지

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_UNAUTHORIZED_VISIT);
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 웨이팅 착석 시간(updatedAt) 48시간 초과")
    void createReview_Fail_ReviewTimeExpired() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(waitingId, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now().minusHours(49)); // 48시간 초과

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        lenient().when(mockWaiting.getMember()).thenReturn(mockMember); // UnnecessaryStubbingException 방지

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_TIME_EXPIRED);
    }

    @Test
    @DisplayName("리뷰 작성 실패 - 이미 작성된 리뷰가 존재함")
    void createReview_Fail_ReviewAlreadyExists() {
        // given
        String email = "member@test.com";
        Long waitingId = 1L;
        ReviewCreateRequestDto requestDto = createReviewCreateRequestDto(waitingId, "맛있어요!", 5);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(waitingId, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        lenient().when(mockWaiting.getMember()).thenReturn(mockMember); // UnnecessaryStubbingException 방지
        when(reviewRepository.existsByWaitingId(waitingId)).thenReturn(true); // 이미 리뷰 존재

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.createReview(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    // ================= [ 가게별 리뷰 조회 (Read) ] =================

    @Test
    @DisplayName("가게별 리뷰 조회 성공")
    void getStoreReviews_Success() {
        // given
        Long storeId = 10L;
        Pageable pageable = PageRequest.of(0, 10);

        Member mockMember = createMockMember(1L, "member@test.com", "테스트유저");
        Store mockStore = createMockStore(storeId, "테스트식당");
        Waiting mockWaiting = createMockWaiting(1L, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(100L, mockMember, mockStore, mockWaiting, "맛있어요!", 5);

        Slice<Review> reviewSlice = new SliceImpl<>(List.of(mockReview), pageable, false);

        when(reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId, pageable)).thenReturn(reviewSlice);

        // when
        Slice<ReviewResponseDto> result = reviewService.getStoreReviews(storeId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).reviewId()).isEqualTo(100L);
        verify(reviewRepository, times(1)).findAllByStoreIdOrderByCreatedAtDesc(storeId, pageable);
    }

    @Test
    @DisplayName("가게별 리뷰 조회 성공 - 리뷰 없음")
    void getStoreReviews_Success_NoReviews() {
        // given
        Long storeId = 10L;
        Pageable pageable = PageRequest.of(0, 10);

        Slice<Review> emptyReviewSlice = new SliceImpl<>(Collections.emptyList(), pageable, false);

        when(reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId, pageable)).thenReturn(emptyReviewSlice);

        // when
        Slice<ReviewResponseDto> result = reviewService.getStoreReviews(storeId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(reviewRepository, times(1)).findAllByStoreIdOrderByCreatedAtDesc(storeId, pageable);
    }

    // ================= [ 리뷰 삭제 (Delete) ] =================

    @Test
    @DisplayName("리뷰 삭제 성공 - 본인의 리뷰 삭제")
    void deleteReview_Success() {
        // given
        Long reviewId = 100L;
        String email = "member@test.com";

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(1L, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(reviewId, mockMember, mockStore, mockWaiting, "맛있어요!", 5);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(mockReview));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        doNothing().when(reviewRepository).delete(mockReview);

        // when
        reviewService.deleteReview(reviewId, email);

        // then
        verify(reviewRepository, times(1)).delete(mockReview);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 존재하지 않는 리뷰")
    void deleteReview_Fail_ReviewNotFound() {
        // given
        Long reviewId = 100L;
        String email = "member@test.com";

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.deleteReview(reviewId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 존재하지 않는 회원")
    void deleteReview_Fail_MemberNotFound() {
        // given
        Long reviewId = 100L;
        String email = "nonexistent@test.com";

        Member mockMember = createMockMember(1L, "existing@test.com", "테스트유저"); // 리뷰 작성자
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(1L, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(reviewId, mockMember, mockStore, mockWaiting, "맛있어요!", 5);

        lenient().when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(mockReview));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.deleteReview(reviewId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 본인의 리뷰가 아님 (권한 없음)")
    void deleteReview_Fail_NotYourReview() {
        // given
        Long reviewId = 100L;
        String email = "hacker@test.com";

        Member originalReviewer = createMockMember(1L, "reviewer@test.com", "원작성자");
        Member hacker = createMockMember(2L, email, "해커");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(1L, originalReviewer, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(reviewId, originalReviewer, mockStore, mockWaiting, "맛있어요!", 5);

        lenient().when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(mockReview));
        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(hacker));
        lenient().when(mockReview.getMember()).thenReturn(originalReviewer); // UnnecessaryStubbingException 방지

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.deleteReview(reviewId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_YOUR_REVIEW);
    }

    // ================= [ 내 리뷰 목록 조회 (Read) ] =================

    @Test
    @DisplayName("내 리뷰 목록 조회 성공")
    void getMyReviews_Success() {
        // given
        String email = "member@test.com";
        Pageable pageable = PageRequest.of(0, 10);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Store mockStore = createMockStore(10L, "테스트식당");
        Waiting mockWaiting = createMockWaiting(1L, mockMember, mockStore, WaitingStatus.SEATED, LocalDateTime.now());
        Review mockReview = createMockReview(100L, mockMember, mockStore, mockWaiting, "내 리뷰", 4);

        Slice<Review> reviewSlice = new SliceImpl<>(List.of(mockReview), pageable, false);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(reviewRepository.findAllByMemberIdOrderByCreatedAtDesc(mockMember.getId(), pageable)).thenReturn(reviewSlice);

        // when
        Slice<ReviewResponseDto> result = reviewService.getMyReviews(email, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).content()).isEqualTo("내 리뷰");
        verify(reviewRepository, times(1)).findAllByMemberIdOrderByCreatedAtDesc(mockMember.getId(), pageable);
    }

    @Test
    @DisplayName("내 리뷰 목록 조회 실패 - 존재하지 않는 회원")
    void getMyReviews_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@test.com";
        Pageable pageable = PageRequest.of(0, 10);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> reviewService.getMyReviews(email, pageable));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("내 리뷰 목록 조회 성공 - 작성한 리뷰 없음")
    void getMyReviews_Success_NoReviews() {
        // given
        String email = "member@test.com";
        Pageable pageable = PageRequest.of(0, 10);

        Member mockMember = createMockMember(1L, email, "테스트유저");
        Slice<Review> emptyReviewSlice = new SliceImpl<>(Collections.emptyList(), pageable, false);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(reviewRepository.findAllByMemberIdOrderByCreatedAtDesc(mockMember.getId(), pageable)).thenReturn(emptyReviewSlice);

        // when
        Slice<ReviewResponseDto> result = reviewService.getMyReviews(email, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(reviewRepository, times(1)).findAllByMemberIdOrderByCreatedAtDesc(mockMember.getId(), pageable);
    }

    // ================= [ Helper Methods ] =================

    private Member createMockMember(Long id, String email, String nickname) {
        Member mockMember = mock(Member.class);
        lenient().when(mockMember.getId()).thenReturn(id);
        lenient().when(mockMember.getEmail()).thenReturn(email);
        lenient().when(mockMember.getNickname()).thenReturn(nickname);
        return mockMember;
    }

    private Store createMockStore(Long id, String name) {
        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getId()).thenReturn(id);
        lenient().when(mockStore.getName()).thenReturn(name);
        return mockStore;
    }

    private Waiting createMockWaiting(Long id, Member member, Store store, WaitingStatus status, LocalDateTime updatedAt) {
        Waiting mockWaiting = mock(Waiting.class);
        lenient().when(mockWaiting.getId()).thenReturn(id);
        lenient().when(mockWaiting.getMember()).thenReturn(member);
        lenient().when(mockWaiting.getStore()).thenReturn(store);
        lenient().when(mockWaiting.getStatus()).thenReturn(status);
        lenient().when(mockWaiting.getUpdatedAt()).thenReturn(updatedAt);
        return mockWaiting;
    }

    private Review createMockReview(Long id, Member member, Store store, Waiting waiting, String content, int rating) {
        Review mockReview = mock(Review.class);
        lenient().when(mockReview.getId()).thenReturn(id);
        lenient().when(mockReview.getMember()).thenReturn(member);
        lenient().when(mockReview.getStore()).thenReturn(store);
        lenient().when(mockReview.getWaiting()).thenReturn(waiting);
        lenient().when(mockReview.getContent()).thenReturn(content);
        lenient().when(mockReview.getRating()).thenReturn(rating);
        return mockReview;
    }

    private ReviewCreateRequestDto createReviewCreateRequestDto(Long waitingId, String content, int rating) {
        return new ReviewCreateRequestDto(waitingId, content, rating);
    }
}
