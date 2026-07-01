package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualCreateRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualResponseDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualUpdateRequestDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객지원 매뉴얼 관리 서비스. 운영자(ADMIN) 전용 CRUD.
 * 권한 검증은 SecurityConfig(`/api/v1/admin/**` hasRole("ADMIN"))가 1차 담당하므로
 * 서비스에는 소유권/권한 검증 로직을 두지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportManualService {

    private final SupportManualRepository supportManualRepository;

    @Transactional
    public Long create(SupportManualCreateRequestDto request) {
        SupportManual manual = SupportManual.builder()
                .category(request.category())
                .question(request.question())
                .answer(request.answer())
                .keywords(request.keywords())
                .build();

        return supportManualRepository.save(manual).getId();
    }

    public List<SupportManualResponseDto> getAll(String category) {
        List<SupportManual> manuals = (category == null || category.isBlank())
                ? supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()
                : supportManualRepository.findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category);

        return manuals.stream()
                .map(SupportManualResponseDto::from)
                .toList();
    }

    @Transactional
    public void update(Long id, SupportManualUpdateRequestDto request) {
        SupportManual manual = findOrThrow(id);
        manual.update(request.category(), request.question(), request.answer(), request.keywords());
    }

    @Transactional
    public void delete(Long id) {
        SupportManual manual = findOrThrow(id);
        manual.softDelete();
    }

    private SupportManual findOrThrow(Long id) {
        return supportManualRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_MANUAL_NOT_FOUND));
    }
}
