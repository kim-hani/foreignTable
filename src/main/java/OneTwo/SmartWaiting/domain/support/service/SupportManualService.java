package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualCreateRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualResponseDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualUpdateRequestDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.event.SupportManualIndexEvent;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객지원 매뉴얼 CRUD. ADMIN만 쓴다.
 * 권한 체크는 SecurityConfig(/api/v1/admin/** hasRole("ADMIN"))에서 걸러주니 여기선 따로 안 본다.
 *
 * 진짜 원본은 support_manual 테이블이고, vector_store는 검색용으로 파생시킨 사본이다.
 * 벡터 색인은 여기서 바로 부르지 않고 SupportManualIndexEvent만 던진다.
 * 실제 색인은 SupportManualIndexer가 커밋된 다음에 처리한다 — 이래야 롤백났을 때 벡터만 남는 사고를 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportManualService {

    private final SupportManualRepository supportManualRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long create(SupportManualCreateRequestDto request) {
        SupportManual manual = SupportManual.builder()
                .category(request.category())
                .question(request.question())
                .answer(request.answer())
                .keywords(request.keywords())
                .build();

        SupportManual saved = supportManualRepository.save(manual);

        // 색인은 커밋 끝나고 리스너가 DB 최신 기준으로 맞춘다 (롤백나면 색인도 안 됨)
        eventPublisher.publishEvent(SupportManualIndexEvent.of(saved.getId()));

        return saved.getId();
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

        // 재색인도 마찬가지로 커밋 후에
        eventPublisher.publishEvent(SupportManualIndexEvent.of(id));
    }

    @Transactional
    public void delete(Long id) {
        SupportManual manual = findOrThrow(id);
        manual.softDelete();

        // 벡터 제거도 커밋 후에
        eventPublisher.publishEvent(SupportManualIndexEvent.of(id));
    }

    private SupportManual findOrThrow(Long id) {
        return supportManualRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_MANUAL_NOT_FOUND));
    }
}
