package OneTwo.SmartWaiting.domain.support.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객지원 매뉴얼. 운영자(ADMIN)가 작성/관리하며, AI 챗봇의 지식 출처로 사용된다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupportManual extends BaseEntity {

    @Column(nullable = false)
    private String category;   // 예: "예약", "환불", "웨이팅"

    @Column(nullable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    private String keywords;   // 검색용 키워드(공백/쉼표 구분)

    public void update(String category, String question, String answer, String keywords) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.keywords = keywords;
    }
}
