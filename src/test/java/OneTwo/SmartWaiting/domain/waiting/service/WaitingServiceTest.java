package OneTwo.SmartWaiting.domain.waiting.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
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
}