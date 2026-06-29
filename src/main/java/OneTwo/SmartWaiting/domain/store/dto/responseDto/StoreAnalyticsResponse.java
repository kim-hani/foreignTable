package OneTwo.SmartWaiting.domain.store.dto.responseDto;

import java.util.List;
import java.util.Map;

/**
 * 사장님(OWNER) 영업 분석 대시보드 응답 DTO.
 *
 * @param days                  집계 기간 (일 단위, 예: 7 / 30)
 * @param totalWaitings         기간 내 총 웨이팅 등록 수
 * @param averageWaitMinutes    CALL→SEATED 평균 대기 시간(분). SEATED 기록이 없으면 null
 * @param noshowRate            노쇼율(%, 0.0~100.0). 종료된 웨이팅이 없으면 null
 * @param top3BusyHours         혼잡 TOP 3 시간대 (등록 건수 내림차순)
 * @param headCountDistribution 인원수별 등록 건수 분포 (key: 인원수, value: 건수)
 */
public record StoreAnalyticsResponse(
        int days,
        long totalWaitings,
        Double averageWaitMinutes,
        Double noshowRate,
        List<BusyHourEntry> top3BusyHours,
        Map<Integer, Long> headCountDistribution
) {
    /**
     * 혼잡 시간대 한 건. hour는 0~23.
     */
    public record BusyHourEntry(int hour, long count) {
    }
}
