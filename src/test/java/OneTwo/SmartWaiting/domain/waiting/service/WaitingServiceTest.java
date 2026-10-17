package OneTwo.SmartWaiting.domain.waiting.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingConnectionCloseEvent;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingMessageEvent;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingQueueChangedEvent;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
    private ApplicationEventPublisher eventPublisher;

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
        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(eq(100L), eq(storeId), any())).thenReturn(false);
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
        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(eq(100L), eq(storeId), any())).thenReturn(true);

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
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(eq(100L), eq(storeId), any())).thenReturn(false);
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
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(eq(100L), eq(storeId), any())).thenReturn(false);

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

    private Store givenRegistrableStore(Long storeId, String email) {
        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        when(mockStore.getMaxWaitingCount()).thenReturn(null);

        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(100L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(eq(100L), eq(storeId), any())).thenReturn(false);
        when(waitingRepository.save(any(Waiting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return mockStore;
    }

    @Test
    @DisplayName("대기 번호 발급 - 앞 팀이 빠져 남은 팀 수가 줄어도 이미 발급된 번호를 재발급하지 않는다.")
    void registerWaiting_QueueNumber_NotReusedAfterOthersLeave() {
        // given: 오늘 5번까지 발급됐지만 지금 남은 팀은 1팀
        String email = "test@gmail.com";
        Long storeId = 1L;
        givenRegistrableStore(storeId, email);
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(1L);
        when(waitingRepository.findMaxQueueNumberSince(eq(storeId), any(LocalDateTime.class))).thenReturn(5);

        // when
        waitingService.registerWaiting(new WaitingRegisterRequestDto(storeId, 2), email);

        // then: 2번(남은 팀 수 + 1)이 아니라 6번, 예상 대기는 앞 1팀 기준
        ArgumentCaptor<Waiting> captor = ArgumentCaptor.forClass(Waiting.class);
        verify(waitingRepository).save(captor.capture());
        assertThat(captor.getValue().getQueueNumber()).isEqualTo(6);
        assertThat(captor.getValue().getExpectedWaitMin()).isEqualTo(10);
    }

    @Test
    @DisplayName("대기 번호 발급 - 영업일 첫 손님은 1번, 앞 팀이 없으므로 예상 대기는 0분이다.")
    void registerWaiting_QueueNumber_FirstOfBusinessDayIsOne() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        givenRegistrableStore(storeId, email);
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(0L);
        when(waitingRepository.findMaxQueueNumberSince(eq(storeId), any(LocalDateTime.class))).thenReturn(0);

        // when
        waitingService.registerWaiting(new WaitingRegisterRequestDto(storeId, 2), email);

        // then
        ArgumentCaptor<Waiting> captor = ArgumentCaptor.forClass(Waiting.class);
        verify(waitingRepository).save(captor.capture());
        assertThat(captor.getValue().getQueueNumber()).isEqualTo(1);
        assertThat(captor.getValue().getExpectedWaitMin()).isZero();
    }

    @Test
    @DisplayName("3번째 안내 - 대기 번호와 무관하게 앞 팀이 2팀이면 등록 즉시 3번째 안내를 보낸다.")
    void registerWaiting_ThirdInLineByTeamsAhead_SendsNotice() {
        // given: 대기 번호는 8번이지만 앞에 2팀
        String email = "test@gmail.com";
        Long storeId = 1L;
        givenRegistrableStore(storeId, email);
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(2L);
        when(waitingRepository.findMaxQueueNumberSince(eq(storeId), any(LocalDateTime.class))).thenReturn(7);

        // when
        waitingService.registerWaiting(new WaitingRegisterRequestDto(storeId, 2), email);

        // then
        verify(eventPublisher).publishEvent(argThat((Object e) ->
                e instanceof WaitingMessageEvent m && m.memberId().equals(100L) && m.message().contains("3번째")));
    }

    @Test
    @DisplayName("3번째 안내 - 대기 번호가 3번이어도 앞 팀이 2팀이 아니면 안내하지 않는다.")
    void registerWaiting_QueueNumberThreeButNotThird_NoNotice() {
        // given: 1·2번이 이미 입장해 앞 팀 0팀, 새 번호는 3번
        String email = "test@gmail.com";
        Long storeId = 1L;
        givenRegistrableStore(storeId, email);
        when(waitingRepository.countByStoreIdAndStatusIn(eq(storeId), any())).thenReturn(0L);
        when(waitingRepository.findMaxQueueNumberSince(eq(storeId), any(LocalDateTime.class))).thenReturn(2);

        // when
        waitingService.registerWaiting(new WaitingRegisterRequestDto(storeId, 2), email);

        // then
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("영업일 경계 - 새벽 4시 이전은 전날 4시, 4시 이후는 당일 4시부터 번호를 이어서 발급한다.")
    void businessDayStart_SplitsAtFourAm() {
        assertThat(WaitingService.businessDayStart(LocalDateTime.of(2026, 10, 16, 3, 59)))
                .isEqualTo(LocalDateTime.of(2026, 10, 15, 4, 0));
        assertThat(WaitingService.businessDayStart(LocalDateTime.of(2026, 10, 16, 4, 0)))
                .isEqualTo(LocalDateTime.of(2026, 10, 16, 4, 0));
        assertThat(WaitingService.businessDayStart(LocalDateTime.of(2026, 10, 16, 23, 30)))
                .isEqualTo(LocalDateTime.of(2026, 10, 16, 4, 0));
    }

    @Test
    @DisplayName("중복 등록 - 대기 또는 호출 상태인 웨이팅이 있으면 WAITING/CALL 모두 기준으로 차단한다.")
    void registerWaiting_ActiveIncludesCall_ChecksBothStatuses() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getIsAcceptingWaiting()).thenReturn(true);
        when(mockStore.getMaxWaitingCount()).thenReturn(null);
        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(100L);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(
                100L, storeId, List.of(WaitingStatus.WAITING, WaitingStatus.CALL))).thenReturn(true);

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> waitingService.registerWaiting(new WaitingRegisterRequestDto(storeId, 2), email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_ALREADY_EXISTS);
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
    @DisplayName("웨이팅 취소 성공 - 1등이 취소하면 새 1등 알림 이벤트를 발행하고 본인 연결 종료를 요청한다")
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



        waitingService.cancelWaiting(waitingId, email);

        // 새 3번째·1번째 알림은 커밋 후 리스너가 처리하도록 이벤트로 발행, 취소한 본인 연결은 종료
        verify(eventPublisher).publishEvent(new WaitingQueueChangedEvent(storeId, true, true));
        verify(eventPublisher).publishEvent(new WaitingConnectionCloseEvent(100L));
    }

    @Test
    @DisplayName("웨이팅 취소 성공 - 2등이 취소하면 새 3번째 알림만 요청하고 1등 알림은 요청하지 않는다")
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


        waitingService.cancelWaiting(waitingId, email);

        verify(eventPublisher).publishEvent(new WaitingQueueChangedEvent(storeId, true, false));
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
    @DisplayName("웨이팅 상태 변경 성공 - 1등 착석 시 새 1등 알림 이벤트를 발행하고 연결 종료를 요청한다")
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



        waitingService.changeStatus(waitingId, requestDto, email);

        verify(eventPublisher).publishEvent(new WaitingQueueChangedEvent(storeId, true, true));
        verify(eventPublisher).publishEvent(new WaitingConnectionCloseEvent(200L));
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
    @DisplayName("웨이팅 미루기 성공 - 1등이 미루면 새 1등 알림 이벤트를 발행한다")
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



        waitingService.postponeWaiting(waitingId, email);

        // 미루기는 대기열에 남아 있으므로 연결을 종료하지 않음
        verify(eventPublisher).publishEvent(new WaitingQueueChangedEvent(storeId, true, true));
        verify(eventPublisher, never()).publishEvent(any(WaitingConnectionCloseEvent.class));
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

    // ==================== getWaitingStatus ====================

    @Test
    @DisplayName("웨이팅 현황 조회 성공 - WAITING 상태이면 currentRank·teamsAhead·expectedWaitMin을 실시간 계산한다")
    void getWaitingStatus_Success_WaitingStatus() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(1L);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(2L);

        var result = waitingService.getWaitingStatus(waitingId, email);

        assertThat(result.status()).isEqualTo(WaitingStatus.WAITING);
        assertThat(result.teamsAhead()).isEqualTo(2L);
        assertThat(result.currentRank()).isEqualTo(3);   // teamsAhead + 1
        assertThat(result.expectedWaitMin()).isEqualTo(20); // 2팀 * 10분
    }

    @Test
    @DisplayName("웨이팅 현황 조회 성공 - CALL 상태에서도 실시간 순번을 계산한다")
    void getWaitingStatus_Success_CallStatus() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);
        Store mockStore = mock(Store.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.CALL);
        when(mockWaiting.getStore()).thenReturn(mockStore);
        when(mockStore.getId()).thenReturn(1L);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any())).thenReturn(0L);

        var result = waitingService.getWaitingStatus(waitingId, email);

        assertThat(result.status()).isEqualTo(WaitingStatus.CALL);
        assertThat(result.teamsAhead()).isEqualTo(0L);
        assertThat(result.currentRank()).isEqualTo(1);
        assertThat(result.expectedWaitMin()).isEqualTo(0);
    }

    @Test
    @DisplayName("웨이팅 현황 조회 성공 - SEATED 상태이면 rank 관련 필드는 모두 0이다")
    void getWaitingStatus_Success_SeatedStatus_ReturnsZero() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.SEATED);

        var result = waitingService.getWaitingStatus(waitingId, email);

        assertThat(result.status()).isEqualTo(WaitingStatus.SEATED);
        assertThat(result.currentRank()).isEqualTo(0);
        assertThat(result.teamsAhead()).isEqualTo(0L);
        assertThat(result.expectedWaitMin()).isEqualTo(0);
        verify(waitingRepository, never()).countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any());
    }

    @Test
    @DisplayName("웨이팅 현황 조회 성공 - CANCEL 상태이면 rank 관련 필드는 모두 0이다")
    void getWaitingStatus_Success_CancelStatus_ReturnsZero() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.CANCEL);

        var result = waitingService.getWaitingStatus(waitingId, email);

        assertThat(result.status()).isEqualTo(WaitingStatus.CANCEL);
        assertThat(result.currentRank()).isEqualTo(0);
        assertThat(result.teamsAhead()).isEqualTo(0L);
        assertThat(result.expectedWaitMin()).isEqualTo(0);
        verify(waitingRepository, never()).countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any());
    }

    @Test
    @DisplayName("웨이팅 현황 조회 성공 - NOSHOW 상태이면 rank 관련 필드는 모두 0이다")
    void getWaitingStatus_Success_NoShowStatus_ReturnsZero() {
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);
        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.NOSHOW);

        var result = waitingService.getWaitingStatus(waitingId, email);

        assertThat(result.status()).isEqualTo(WaitingStatus.NOSHOW);
        assertThat(result.currentRank()).isEqualTo(0);
        assertThat(result.teamsAhead()).isEqualTo(0L);
        assertThat(result.expectedWaitMin()).isEqualTo(0);
        verify(waitingRepository, never()).countByStoreIdAndStatusInAndTicketTimeLessThan(any(), any(), any());
    }

    @Test
    @DisplayName("웨이팅 현황 조회 실패 - 존재하지 않는 waitingId이면 WAITING_NOT_FOUND 예외 발생")
    void getWaitingStatus_Fail_WaitingNotFound() {
        Long waitingId = 99L;
        String email = "test@gmail.com";

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.empty());

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> waitingService.getWaitingStatus(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_NOT_FOUND);
    }

    @Test
    @DisplayName("웨이팅 현황 조회 실패 - 타인의 waitingId 접근 시 NOT_YOUR_WAITING 예외 발생")
    void getWaitingStatus_Fail_NotYourWaiting() {
        Long waitingId = 1L;
        String email = "hacker@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member owner = mock(Member.class);
        Member hacker = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(hacker));
        when(mockWaiting.getMember()).thenReturn(owner);
        when(owner.getId()).thenReturn(100L);
        when(hacker.getId()).thenReturn(999L);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> waitingService.getWaitingStatus(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_YOUR_WAITING);
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
