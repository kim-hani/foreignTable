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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("웨이팅 등록 성공 - 순차적으로 대기열 번호 부여")
    void registerWaiting_Success() {

        // given
        String email = "test@gmail.com";
        Long storeId = 1L;

        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(mockStore.getId()).thenReturn(storeId);
        Mockito.when(mockStore.getAverageWaiting()).thenReturn(10);

        Member mockMember = mock(Member.class);
        Mockito.when(mockMember.getId()).thenReturn(100L);

        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(false);

        Mockito.when(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).thenReturn(5L);

        Waiting mockSavedWaiting = mock(Waiting.class);
        Mockito.when(mockSavedWaiting.getId()).thenReturn(999L);
        Mockito.when(waitingRepository.save(Mockito.any(Waiting.class))).thenReturn(mockSavedWaiting);

        //when
        Long resultId = waitingService.registerWaiting(requestDto, email);

        // then
        assertThat(resultId).isEqualTo(999L);

        verify(waitingRepository, times(1)).save(Mockito.any(Waiting.class));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 존재 하지 않는 식당")
    void registerWaiting_Fail_StoreNotFound() {

        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 존재 하지 않는 회원")
    void registerWaiting_Fail_MemberNotFound() {

        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 등록 실패 - 이미 대기 중인 웨이팅 존재")
    void registerWaiting_Fail_WaitingAlreadyExists() {

        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        WaitingRegisterRequestDto requestDto = WaitingRegisterRequestDto.builder()
                .storeId(storeId)
                .headCount(4)
                .build();

        Store mockStore = mock(Store.class);
        Mockito.when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        Mockito.when(mockStore.getId()).thenReturn(storeId);

        Member mockMember = mock(Member.class);
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        Mockito.when(mockMember.getId()).thenReturn(100L);

        Mockito.when(waitingRepository.existsByMemberIdAndStoreIdAndStatus(100L, storeId, WaitingStatus.WAITING)).thenReturn(true);

        // when & then
        Assertions.assertThrows(BusinessException.class, () -> waitingService.registerWaiting(requestDto, email));
    }

    @Test
    @DisplayName("웨이팅 취소 성공 - 상태가 CANCEL로 변경된다.")
    void cancelWaiting_Success() {

        // given
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(mockMember.getId()).thenReturn(100L);

        // when
        waitingService.cancelWaiting(waitingId, email);

        // then
        verify(mockWaiting, times(1)).changeStatus(WaitingStatus.CANCEL);
    }

    @Test
    @DisplayName("웨이팅 취소 실패 - 존재 하지 않는 웨이팅")
    void cancelWaiting_Fail_WaitingNotFound() {

        // given
        Long waitingId = 1L;
        String email = "test.gmail.com";

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.empty());

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.cancelWaiting(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WAITING_NOT_FOUND);
    }

    @Test
    @DisplayName("웨이팅 취소 실패 - 본인의 웨이팅 아님")
    void cancelWaiting_Fail_NotYourWaiting() {

        // given
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

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.cancelWaiting(waitingId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_YOUR_WAITING);
    }

    @Test
    @DisplayName("웨이팅 상태 변경 성공 - 사장님이 상태를 변경한다.")
    void changeStatus_Success() {

        // given
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

        // when
        waitingService.changeStatus(waitingId, requestDto, email);

        // then
        verify(mockWaiting, times(1)).changeStatus(WaitingStatus.SEATED);
    }

    @Test
    @DisplayName("웨이팅 상태 변경 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void changeStatus_Fail_UnauthorizedStoreOwner() {

        // given
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

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> waitingService.changeStatus(waitingId, requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    @Test
    @DisplayName("웨이팅 미루기 성공 ")
    void postponeWaiting_Success() {

        // given
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        Mockito.when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        Mockito.when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        Mockito.when(mockWaiting.getMember()).thenReturn(mockMember);
        Mockito.when(mockMember.getId()).thenReturn(100L);
        Mockito.when(mockWaiting.getStatus()).thenReturn(WaitingStatus.WAITING);

        // when
        waitingService.postponeWaiting(waitingId, email);

        // then
        verify(mockWaiting, times(1)).postpone();

    }

    @Test
    @DisplayName("웨이팅 미루기 실패 - 현재 대기 중(WAITING)인 상태가 아님")
    void postponeWaiting_Fail_InvalidStatus() {

        // given
        Long waitingId = 1L;
        String email = "test@gmail.com";

        Waiting mockWaiting = mock(Waiting.class);
        Member mockMember = mock(Member.class);

        when(waitingRepository.findById(waitingId)).thenReturn(Optional.of(mockWaiting));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getId()).thenReturn(100L);

        when(mockWaiting.getStatus()).thenReturn(WaitingStatus.SEATED);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () -> waitingService.postponeWaiting(waitingId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_WAITING_STATUS);
    }

    @Test
    @DisplayName("내 웨이팅 조회 성공 - 내 앞 대기팀과 예상 대기시간이 계산된다.")
    void getMyWaitings_Success() {

        // given
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

        Mockito.when(waitingRepository.countByStoreIdAndStatusAndTicketTimeLessThan(any(), any(), any()))
                .thenReturn(2L);

        when(mockWaiting.getId()).thenReturn(10L);
        when(mockStore.getName()).thenReturn("맛있는 식당");
        when(mockWaiting.getMember()).thenReturn(mockMember);
        when(mockMember.getNickname()).thenReturn("손님1");
        when(mockWaiting.getHeadCount()).thenReturn(2);
        when(mockWaiting.getPostponedCount()).thenReturn(0);

        // when
        List<WaitingResponse> responses = waitingService.getMyWaitings(email);

        // then
        assertThat(responses).hasSize(1);

        assertThat(responses.get(0).expectedWaitMin()).isEqualTo(20); // 2팀 * 10분 = 20분 예상 대기시간

    }

    @Test
    @DisplayName("가게 웨이팅 조회 성공 - 사장님 본인의 가게 대기열을 조회한다")
    void getStoreWaitings_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";

        Member mockOwner = mock(Member.class);
        Store mockStore = mock(Store.class);
        Waiting mockWaiting = mock(Waiting.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

        when(mockStore.getOwnerId()).thenReturn(200L);
        when(mockOwner.getId()).thenReturn(200L);

        when(waitingRepository.findAllByStoreIdAndStatus(storeId, WaitingStatus.WAITING))
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

        // when
        List<WaitingResponse> responses = waitingService.getStoreWaitings(storeId, WaitingStatus.WAITING, email);

        // then
        assertThat(responses).hasSize(1);
    }
}