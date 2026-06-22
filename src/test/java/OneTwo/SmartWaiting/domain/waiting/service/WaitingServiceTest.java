package OneTwo.SmartWaiting.domain.waiting.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {

    @InjectMocks
    private WaitingService waitingService;

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private FcmService fcmService;

    // ==================== registerWaiting ====================

    @Test
    @DisplayName("웨이팅 등록 성공 - 순차적으로 대기열 번호 부여")
    void registerWaiting_Success() {
        String email = "test@gmail.com";
        Long storeId = 1L;

        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(mockStore.getId()).thenReturn(storeId);
        Mockito.when(mockStore.getAverageWaiting()).thenReturn(10);
        Mockito.when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        Mockito.when(mockStore.getMaxWaitingCount()).thenReturn(null);

        Member mockMember = mock(Member.class);
        Mockito.when(mockMember.getId()).thenReturn(100L);

        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(false);
        Mockito.when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(5L);

        Waiting mockSavedWaiting = mock(Waiting.class);
        Mockito.when(mockSavedWaiting.getId()).thenReturn(999L);
        Mockito.when(waitingRepository.save(Mockito.any(Waiting.class))).thenReturn(mockSavedWaiting);

        Long resultId = waitingService.registerWaiting(requestDto, email);

        assertThat(resultId).isEqualTo(999L);
        verify(waitingRepository, times(1)).save(Mockito.any(Waiting.class));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 존재하지 않는 식당")
    void registerWaiting_Fail_StoreNotFound() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 존재하지 않는 회원")
    void registerWaiting_Fail_MemberNotFound() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 웨이팅 접수가 중단된 식당")
    void registerWaiting_Fail_StoreNotAcceptingWaiting() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(2)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        Mockito.when(mockStore.getIsAcceptingWaiting()).thenReturn(false);

        Member mockMember = mock(Member.class);
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> waitingService.registerWaiting(requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_ACCEPTING_WAITING);
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 이미 대기 중인 웨이팅 존재")
    void registerWaiting_Fail_WaitingAlreadyExists() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        Mockito.when(mockStore.getId()).thenReturn(storeId);
        Mockito.when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        Mockito.when(mockStore.getMaxWaitingCount()).thenReturn(null);

        Member mockMember = mock(Member.class);
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(true);

        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 등록 성공 - 최대 대기 팀 수 미설정(null)이면 제한 없이 등록된다")
    void registerWaiting_Success_NoMaxWaitingCount() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId).headCount(2).build();

        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        when(mockStore.getMaxWaitingCount()).thenReturn(null);

        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(100L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(false);
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(3L);

        Waiting mockSaved = mock(Waiting.class);
        when(mockSaved.getId()).thenReturn(1L);
        when(waitingRepository.save(any(Waiting.class))).thenReturn(mockSaved);

        Long resultId = waitingService.registerWaiting(requestDto, email);

        assertThat(resultId).isEqualTo(1L);
        verify(waitingRepository, times(1)).save(any(Waiting.class));
    }

    @Test
    @DisplayName("웨이팅 등록 성공 - 현재 대기 수가 상한 미만이면 등록된다")
    void registerWaiting_Success_UnderMaxWaitingCount() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId).headCount(2).build();

        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        when(mockStore.getMaxWaitingCount()).thenReturn(10);

        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(100L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(9L);
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(false);

        Waiting mockSaved = mock(Waiting.class);
        when(mockSaved.getId()).thenReturn(2L);
        when(waitingRepository.save(any(Waiting.class))).thenReturn(mockSaved);

        Long resultId = waitingService.registerWaiting(requestDto, email);

        assertThat(resultId).isEqualTo(2L);
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 현재 대기 수가 최대 상한에 도달하면 WAITING_QUEUE_FULL 예외 발생")
    void registerWaiting_Fail_WaitingQueueFull() {
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId).headCount(2).build();

        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        when(mockStore.getMaxWaitingCount()).thenReturn(10);

        Member mockMember = mock(Member.class);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(10L);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> waitingService.registerWaiting(requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_QUEUE_FULL);
    }

    // ==================== cancelWaiting ====================

    @Test
    @DisplayName("웨이팅 취소 성공 - 상태가 CANCEL로 변경된다")
    void cancelWaiting_Success() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(mockWaiting.getStore()).thenReturn(mockStore);
        Mockito.when(mockStore.getId()).thenReturn(1L);
        Mockito.when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        Mockito.when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(5L);

        waitingService.cancelWaiting(waitingId, email);

        verify(mockWaiting, times(1)).changeStatus(WaitingStatus.CANCEL);
    }

    @Test
    @DisplayName("웨이팅 취소 성공 - 1등이 취소하면 새로운 1등에게 SSE·FCM 알림이 발송된다")
    void cancelWaiting_Success_FirstInLineNotified() {
        Long waitingId = 1L;
        String email = "first@gmail.com";
        Long storeId = 1L;

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        // 내가 1등 (앞에 0팀) → isTop3=true, isTop2=true
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(0L);

        // checkAndNotifyThirdInLine: offset=2 → 빈 결과
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 2)))
                .thenReturn(Page.empty());

        // checkAndNotifyFirstInLine: offset=0 → 새 1등
        Waiting newFirst = mock(Waiting.class);
        Member newFirstMember = mock(Member.class);
        when(newFirstMember.getId()).thenReturn(200L);
        when(newFirstMember.getFcmToken()).thenReturn("fcm-token-200");
        when(newFirst.getMember()).thenReturn(newFirstMember);
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 0)))
                .thenReturn(new PageImpl<>(List.of(newFirst)));

        waitingService.cancelWaiting(waitingId, email);

        verify(notificationService, times(1)).sendToClient(eq(200L), any());
        verify(fcmService, times(1)).sendFirstInLinePush(eq("fcm-token-200"), eq(storeId));
    }

    @Test
    @DisplayName("웨이팅 취소 성공 - 2등 이하가 취소하면 1등에게 알림이 발송되지 않는다")
    void cancelWaiting_Success_NoFirstAlert_WhenNotFirst() {
        Long waitingId = 1L;
        String email = "second@gmail.com";
        Long storeId = 1L;

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        // 내가 2등 (앞에 1팀) → isTop3=true, isTop2=false
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(1L);

        // checkAndNotifyThirdInLine: offset=2 → 빈 결과
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING), any(Pageable.class)))
                .thenReturn(Page.empty());

        waitingService.cancelWaiting(waitingId, email);

        verify(fcmService, never()).sendFirstInLinePush(any(), any());
    }

    @Test
    @DisplayName("웨이팅 취소 실패 - 존재하지 않는 웨이팅")
    void cancelWaiting_Fail_WaitingNotFound() {
        Long waitingId = 1L;
        String email = "test.gmail.com";

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.empty());

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.cancelWaiting(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_NOT_FOUND);
    }

    @Test
    @DisplayName("웨이팅 취소 실패 - 본인의 웨이팅 아님")
    void cancelWaiting_Fail_NotYourWaiting() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Member mockHacker = mock(Member.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));
        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(mockHacker.getId()).thenReturn(999L);

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.cancelWaiting(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_YOUR_WAITING);
    }

    // ==================== changeStatus ====================

    @Test
    @DisplayName("웨이팅 상태 변경 성공 - 사장님이 상태를 변경한다")
    void changeStatus_Success() {
        Long waitingId = 1L;
        String email = "test@gmail.com";
        WaitingChangeRequestDto requestDto = new WaitingChangeRequestDto(WaitingStatus.SEATED);

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockWaiting.getStore()).thenReturn(mockStore);
        Mockito.when(mockStore.getOwnerId()).thenReturn(200L);
        Mockito.when(mockMember.getId()).thenReturn(200L);
        Mockito.when(mockStore.getId()).thenReturn(1L);
        Mockito.when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(5L);

        waitingService.changeStatus(waitingId, requestDto, email);

        verify(mockWaiting, times(1)).changeStatus(WaitingStatus.SEATED);
    }

    @Test
    @DisplayName("웨이팅 상태 변경 성공 - 1등 착석 시 새로운 1등에게 SSE·FCM 알림 발송")
    void changeStatus_Success_FirstInLineNotified_WhenSeated() {
        Long waitingId = 1L;
        String email = "owner@gmail.com";
        Long storeId = 1L;
        WaitingChangeRequestDto requestDto = new WaitingChangeRequestDto(WaitingStatus.SEATED);

        Waiting mockWaiting = mock(Waiting.class);
        Member mockOwner = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getOwnerId()).thenReturn(200L);
        when(mockOwner.getId()).thenReturn(200L);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockWaiting.getMember()).thenReturn(mockOwner);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        // 대상이 1등 (teamsAhead == 0) → isTop3=true, isTop2=true
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(0L);

        // checkAndNotifyThirdInLine: offset=2 → 빈 결과
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 2)))
                .thenReturn(Page.empty());

        // checkAndNotifyFirstInLine: offset=0 → 새 1등
        Waiting newFirst = mock(Waiting.class);
        Member newFirstMember = mock(Member.class);
        when(newFirstMember.getId()).thenReturn(300L);
        when(newFirstMember.getFcmToken()).thenReturn("token-300");
        when(newFirst.getMember()).thenReturn(newFirstMember);
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 0)))
                .thenReturn(new PageImpl<>(List.of(newFirst)));

        waitingService.changeStatus(waitingId, requestDto, email);

        verify(notificationService, times(1)).sendToClient(eq(300L), any());
        verify(fcmService, times(1)).sendFirstInLinePush(eq("token-300"), eq(storeId));
    }

    @Test
    @DisplayName("웨이팅 상태 변경 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void changeStatus_Fail_UnauthorizedStoreOwner() {
        Long waitingId = 1L;
        String email = "test@gmail.com";
        WaitingChangeRequestDto requestDto = new WaitingChangeRequestDto(WaitingStatus.SEATED);

        Waiting mockWaiting = mock(Waiting.class);
        Member mockHacker = mock(Member.class);
        Store mockStore = mock(Store.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));
        Mockito.when(mockWaiting.getStore()).thenReturn(mockStore);
        Mockito.when(mockStore.getOwnerId()).thenReturn(200L);
        Mockito.when(mockHacker.getId()).thenReturn(999L);

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.changeStatus(waitingId, requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    // ==================== postponeWaiting ====================

    @Test
    @DisplayName("웨이팅 미루기 성공")
    void postponeWaiting_Success() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        Mockito.when(mockWaiting.getStore()).thenReturn(mockStore);
        Mockito.when(mockStore.getId()).thenReturn(1L);
        Mockito.when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(5L);

        waitingService.postponeWaiting(waitingId, email);

        verify(mockWaiting, times(1)).postpone();
    }

    @Test
    @DisplayName("웨이팅 미루기 성공 - 1등이 미루면 새로운 1등에게 SSE·FCM 알림 발송")
    void postponeWaiting_Success_FirstInLineNotified() {
        Long waitingId = 1L;
        String email = "test@gmail.com";
        Long storeId = 1L;

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(storeId);
        // 내가 1등 → isTop3=true, isTop2=true
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(0L);

        // checkAndNotifyThirdInLine: offset=2 → 빈 결과
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 2)))
                .thenReturn(Page.empty());

        // checkAndNotifyFirstInLine: offset=0 → 새 1등 (FCM 토큰 없음)
        Waiting newFirst = mock(Waiting.class);
        Member newFirstMember = mock(Member.class);
        when(newFirstMember.getId()).thenReturn(400L);
        when(newFirstMember.getFcmToken()).thenReturn(null);
        when(newFirst.getMember()).thenReturn(newFirstMember);
        when(waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                eq(storeId), eq(WaitingStatus.WAITING),
                argThat(p -> p.getOffset() == 0)))
                .thenReturn(new PageImpl<>(List.of(newFirst)));

        waitingService.postponeWaiting(waitingId, email);

        // SSE는 발송되고, FCM은 토큰 없으므로 sendFirstInLinePush 내부에서 early return
        verify(notificationService, times(1)).sendToClient(eq(400L), any());
        verify(fcmService, times(1)).sendFirstInLinePush(eq(null), eq(storeId));
    }

    @Test
    @DisplayName("웨이팅 미루기 실패 - 현재 대기 중(WAITING)인 상태가 아님")
    void postponeWaiting_Fail_InvalidStatus() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.SEATED);

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.postponeWaiting(waitingId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_WAITING_STATUS);
    }

    // ==================== isTop2 ====================

    @Test
    @DisplayName("isTop2 - 내가 1등이면 true를 반환한다")
    void isTop2_ReturnsTrue_WhenFirst() {
        Waiting mockWaiting = mock(Waiting.class);
        Store mockStore = mock(Store.class);

        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(1L);
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(0L);

        assertThat(waitingService.isTop2(mockWaiting)).isTrue();
    }

    @Test
    @DisplayName("isTop2 - 내가 2등이면 false를 반환한다")
    void isTop2_ReturnsFalse_WhenSecond() {
        Waiting mockWaiting = mock(Waiting.class);
        Store mockStore = mock(Store.class);

        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(1L);
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(1L);

        assertThat(waitingService.isTop2(mockWaiting)).isFalse();
    }

    @Test
    @DisplayName("isTop2 - 이미 CANCEL 상태이면 false를 반환한다")
    void isTop2_ReturnsFalse_WhenCancelled() {
        Waiting mockWaiting = mock(Waiting.class);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.CANCEL);

        assertThat(waitingService.isTop2(mockWaiting)).isFalse();
    }

    // ==================== getMyWaitings ====================

    @Test
    @DisplayName("내 웨이팅 조회 성공 - 내 앞 대기팀과 예상 대기시간이 계산된다")
    void getMyWaitings_Success() {
        String email = "test@gmail.com";
        Member mockMember = mock(Member.class);
        Waiting mockWaiting = mock(Waiting.class);
        Store mockStore = mock(Store.class);

        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(waitingRepository.findAllByMemberId(100L)).thenReturn(List.of(mockWaiting));

        Mockito.when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        Mockito.when(mockWaiting.getStore()).thenReturn(mockStore);
        Mockito.when(mockStore.getId()).thenReturn(1L);
        Mockito.when(mockStore.getAverageWaiting()).thenReturn(10);
        Mockito.when(mockWaiting.getCreatedAt()).thenReturn(LocalDateTime.now());
        Mockito.when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(2L);

        when(mockWaiting.getId()).thenReturn(10L);
        when(mockStore.getName()).thenReturn("맛있는 식당");
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getNickname()).thenReturn("손님1");
        when(mockWaiting.getHeadCount()).thenReturn(2);
        when(mockWaiting.getPostponedCount()).thenReturn(0);

        List<WaitingResponse> responses = waitingService.getMyWaitings(email);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).expectedWaitMin()).isEqualTo(20); // 2팀 * 10분
    }

    // ==================== getStoreWaitings ====================

    @Test
    @DisplayName("가게 웨이팅 조회 성공 - 사장님 본인의 가게 대기열을 조회한다")
    void getStoreWaitings_Success() {
        Long storeId = 1L;
        String email = "owner@gmail.com";

        Member mockOwner = mock(Member.class);
        Store mockStore = mock(Store.class);
        Waiting mockWaiting = mock(Waiting.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(mockStore.getOwnerId()).thenReturn(200L);
        when(mockOwner.getId()).thenReturn(200L);
        when(waitingRepository.findAllByStoreIdAndStatusOrderByTicketTimeAsc(storeId, WaitingStatus.WAITING))
                .thenReturn(List.of(mockWaiting));

        when(mockWaiting.getId()).thenReturn(10L);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(1L);
        when(mockStore.getName()).thenReturn("맛있는 식당");
        when(mockWaiting.getMember()).thenReturn(mockOwner);
        when(mockOwner.getNickname()).thenReturn("사장님");
        when(mockWaiting.getHeadCount()).thenReturn(2);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        when(mockWaiting.getPostponedCount()).thenReturn(0);
        when(mockWaiting.getCreatedAt()).thenReturn(LocalDateTime.now());

        List<WaitingResponse> responses = waitingService.getStoreWaitings(storeId, WaitingStatus.WAITING, email);

        assertThat(responses).hasSize(1);
    }
}
