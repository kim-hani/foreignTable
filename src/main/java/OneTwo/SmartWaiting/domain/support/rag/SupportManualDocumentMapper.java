package OneTwo.SmartWaiting.domain.support.rag;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.Map;

/**
 * SupportManual(원본)을 VectorStore가 쓰는 Document로 바꿔주는 변환기.
 *
 * RAG에서는 뭘 벡터로 만드느냐가 검색 품질을 좌우한다.
 * 그래서 질문/답변/키워드를 한 덩어리로 합쳐서 임베딩한다. 이래야 검색이 좀 더 잘 걸린다.
 * metadata에 넣는 manualId/category는 나중에 벡터를 지우거나 카테고리로 거를 때 키로 쓴다.
 */
public final class SupportManualDocumentMapper {

    /** 원본 매뉴얼 id. 수정/삭제할 때 이 값으로 벡터를 찾는다. */
    public static final String META_MANUAL_ID = "manualId";
    /** 카테고리. 검색할 때 필터 키로 쓴다. */
    public static final String META_CATEGORY = "category";

    private SupportManualDocumentMapper() {
    }

    /** 매뉴얼 1건을 임베딩할 Document로 바꾼다. */
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

    /** 특정 매뉴얼 벡터를 지울 때 쓰는 metadata 필터(manualId == id). */
    public static Filter.Expression manualIdFilter(Long manualId) {
        return new FilterExpressionBuilder().eq(META_MANUAL_ID, manualId).build();
    }
}
