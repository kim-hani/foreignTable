package OneTwo.SmartWaiting.domain.support.rag;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.Map;

/**
 * SupportManual(원본 매뉴얼) ↔ VectorStore의 Document(임베딩 단위) 사이의 변환 규칙.
 *
 * <p>RAG의 "인덱싱" 단계에서 무엇을 벡터로 만들지가 검색 품질을 좌우한다.
 * 질문/답변/키워드를 한 문서로 합쳐 임베딩해 검색 재현율을 높인다.
 * metadata의 manualId/category는 삭제·카테고리 필터의 키로 쓰인다.
 */
public final class SupportManualDocumentMapper {

    /** 원본 매뉴얼 식별자 (수정/삭제 시 해당 벡터를 찾는 키). */
    public static final String META_MANUAL_ID = "manualId";
    /** 카테고리 (검색 시 필터링 키). */
    public static final String META_CATEGORY = "category";

    private SupportManualDocumentMapper() {
    }

    /** 매뉴얼 1건을 임베딩 대상 Document로 변환한다. */
    public static Document toDocument(SupportManual manual) {
        String content = String.format(
                "[%s] Q: %s%nA: %s%nkeywords: %s",
                manual.getCategory(),
                manual.getQuestion(),
                manual.getAnswer(),
                manual.getKeywords() == null ? "" : manual.getKeywords()
        );

        return Document.builder()
                .text(content)
                .metadata(Map.of(
                        META_MANUAL_ID, manual.getId(),
                        META_CATEGORY, manual.getCategory()
                ))
                .build();
    }

    /** 특정 매뉴얼의 벡터를 지우기 위한 metadata 필터(manualId == id). */
    public static Filter.Expression manualIdFilter(Long manualId) {
        return new FilterExpressionBuilder().eq(META_MANUAL_ID, manualId).build();
    }
}
