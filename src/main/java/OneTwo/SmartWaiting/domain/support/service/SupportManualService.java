package OneTwo.SmartWaiting.domain.support.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualCreateRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualResponseDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualUpdateRequestDto;
import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.rag.SupportManualDocumentMapper;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객지원 매뉴얼 관리 서비스. 운영자(ADMIN) 전용 CRUD.
 * 권한 검증은 SecurityConfig(`/api/v1/admin/**` hasRole("ADMIN"))가 1차 담당하므로
 * 서비스에는 소유권/권한 검증 로직을 두지 않는다.
 *
 * <p>매뉴얼 원본(support_manual)이 진실의 원천이고, VectorStore(vector_store)는
 * RAG 검색을 위한 파생 인덱스다. 매뉴얼이 바뀔 때마다 벡터를 동기화한다(방식 A: 동기 호출).
 * TODO(#42-followup): 트랜잭션 롤백 시 유령 벡터를 막기 위해 @TransactionalEventListener
 *  (AFTER_COMMIT) 기반 방식 B로 이관.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportManualService {

    private final SupportManualRepository supportManualRepository;
    private final VectorStore vectorStore;

    @Transactional
    public Long create(SupportManualCreateRequestDto request) {
        SupportManual manual = SupportManual.builder()
                .category(request.category())
                .question(request.question())
                .answer(request.answer())
                .keywords(request.keywords())
                .build();

        SupportManual saved = supportManualRepository.save(manual);

        // 인덱싱: 매뉴얼을 임베딩해 벡터 저장소에 적재
        indexManual(saved);

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

        // 재인덱싱: 기존 벡터 제거 후 새 내용으로 다시 적재
        removeFromIndex(id);
        indexManual(manual);
    }

    @Transactional
    public void delete(Long id) {
        SupportManual manual = findOrThrow(id);
        manual.softDelete();

        // 삭제된 매뉴얼이 검색되면 안 되므로 벡터 인덱스에서 제거
        removeFromIndex(id);
    }

    private SupportManual findOrThrow(Long id) {
        return supportManualRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_MANUAL_NOT_FOUND));
    }

    /** 매뉴얼을 Document로 변환해 벡터 저장소에 추가(임베딩은 add 내부에서 자동 수행). */
    private void indexManual(SupportManual manual) {
        try {
            Document document = SupportManualDocumentMapper.toDocument(manual);
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SUPPORT_VECTOR_INDEX_FAILED);
        }
    }

    /** 특정 매뉴얼의 벡터를 metadata 필터로 제거. */
    private void removeFromIndex(Long manualId) {
        try {
            vectorStore.delete(SupportManualDocumentMapper.manualIdFilter(manualId));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SUPPORT_VECTOR_INDEX_FAILED);
        }
    }
}
