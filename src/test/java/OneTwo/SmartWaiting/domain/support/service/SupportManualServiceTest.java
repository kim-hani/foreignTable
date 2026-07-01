package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualCreateRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualResponseDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualUpdateRequestDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportManualServiceTest {

    @InjectMocks
    private SupportManualService supportManualService;

    @Mock
    private SupportManualRepository supportManualRepository;

    private static final Long MANUAL_ID = 1L;

    // ================= [ 생성 ] =================

    @Test
    @DisplayName("매뉴얼 생성 성공 - 저장 후 id를 반환한다.")
    void create_Success() {
        // given
        SupportManualCreateRequestDto request =
                new SupportManualCreateRequestDto("환불", "환불은 어떻게 하나요?", "마이페이지에서 신청 가능합니다.", "환불 결제취소");

        SupportManual savedManual = mock(SupportManual.class);
        when(savedManual.getId()).thenReturn(MANUAL_ID);
        when(supportManualRepository.save(any(SupportManual.class))).thenReturn(savedManual);

        // when
        Long resultId = supportManualService.create(request);

        // then
        assertThat(resultId).isEqualTo(MANUAL_ID);
        verify(supportManualRepository, times(1)).save(any(SupportManual.class));
    }

    // ================= [ 조회 ] =================

    @Test
    @DisplayName("매뉴얼 전체 조회 성공 - category가 null이면 전체 목록을 조회한다.")
    void getAll_Success_AllWhenCategoryNull() {
        // given
        SupportManual manual = SupportManual.builder()
                .category("환불").question("Q").answer("A").keywords("k").build();
        when(supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(manual));

        // when
        List<SupportManualResponseDto> result = supportManualService.getAll(null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("환불");
        verify(supportManualRepository, times(1)).findAllByIsDeletedFalseOrderByCreatedAtDesc();
        verify(supportManualRepository, never())
                .findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("매뉴얼 카테고리 조회 성공 - category가 지정되면 해당 카테고리만 조회한다.")
    void getAll_Success_FilterByCategory() {
        // given
        SupportManual manual = SupportManual.builder()
                .category("웨이팅").question("Q").answer("A").keywords("k").build();
        when(supportManualRepository.findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc("웨이팅"))
                .thenReturn(List.of(manual));

        // when
        List<SupportManualResponseDto> result = supportManualService.getAll("웨이팅");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("웨이팅");
        verify(supportManualRepository, times(1))
                .findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc("웨이팅");
        verify(supportManualRepository, never()).findAllByIsDeletedFalseOrderByCreatedAtDesc();
    }

    // ================= [ 수정 ] =================

    @Test
    @DisplayName("매뉴얼 수정 성공 - 더티 체킹으로 내용을 변경한다.")
    void update_Success() {
        // given
        SupportManual manual = mock(SupportManual.class);
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.of(manual));
        SupportManualUpdateRequestDto request =
                new SupportManualUpdateRequestDto("예약", "수정된 질문", "수정된 답변", "예약 변경");

        // when
        supportManualService.update(MANUAL_ID, request);

        // then
        verify(manual, times(1)).update("예약", "수정된 질문", "수정된 답변", "예약 변경");
    }

    @Test
    @DisplayName("매뉴얼 수정 실패 - 존재하지 않으면 SUPPORT_MANUAL_NOT_FOUND 예외가 발생한다.")
    void update_Fail_NotFound() {
        // given
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());
        SupportManualUpdateRequestDto request =
                new SupportManualUpdateRequestDto("예약", "Q", "A", "k");

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> supportManualService.update(MANUAL_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUPPORT_MANUAL_NOT_FOUND);
    }

    // ================= [ 삭제 ] =================

    @Test
    @DisplayName("매뉴얼 삭제 성공 - 물리 삭제가 아닌 softDelete를 호출한다.")
    void delete_Success_SoftDelete() {
        // given
        SupportManual manual = mock(SupportManual.class);
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.of(manual));

        // when
        supportManualService.delete(MANUAL_ID);

        // then
        verify(manual, times(1)).softDelete();
        verify(supportManualRepository, never()).delete(any(SupportManual.class));
    }

    @Test
    @DisplayName("매뉴얼 삭제 실패 - 존재하지 않으면 SUPPORT_MANUAL_NOT_FOUND 예외가 발생한다.")
    void delete_Fail_NotFound() {
        // given
        when(supportManualRepository.findByIdAndIsDeletedFalse(MANUAL_ID))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> supportManualService.delete(MANUAL_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUPPORT_MANUAL_NOT_FOUND);
    }
}
