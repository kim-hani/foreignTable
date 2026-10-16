package OneTwo.SmartWaiting.domain.support.event;

/**
 * "이 매뉴얼 벡터를 DB 최신 상태에 맞춰 다시 맞춰라"는 신호.
 *
 * SupportManualService가 생성/수정/삭제 트랜잭션 안에서 던지고, SupportManualIndexer가
 * 커밋 후에 받아 처리한다. 뭘 어떻게 색인할지는 그때 DB를 다시 읽어서 정하니까
 * 여기엔 manualId만 있으면 된다 (있으면 재색인, 지워졌으면 벡터 제거).
 */
public record SupportManualIndexEvent(Long manualId) {

    public static SupportManualIndexEvent of(Long manualId) {
        return new SupportManualIndexEvent(manualId);
    }
}
