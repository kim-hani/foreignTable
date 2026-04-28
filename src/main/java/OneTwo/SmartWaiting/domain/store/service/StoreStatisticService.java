package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.domain.store.entity.HourlyStatVo;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.WaitingStatProjection;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreStatisticService {

    private final StoreRepository storeRepository;
    private final WaitingRepository waitingRepository;

    @Transactional
    public void calculateAndSaveStoreStats(Long storeId) {
        Store store = storeRepository.findById(storeId).orElseThrow();

        // 1. 초기 빈 맵 세팅 (모든 요일, 모든 시간에 기본값 0을 채워둠)
        Map<String, List<HourlyStatVo>> statsMap = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<HourlyStatVo> emptyHours = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                emptyHours.add(new HourlyStatVo(i, 0, 0));
            }
            statsMap.put(day.name(), emptyHours);
        }

        // DB에서 계산한 요약 데이터만 가져옴
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(30);
        List<WaitingStatProjection> dbStats = waitingRepository.findWaitingStatsByStoreId(storeId, oneMonthAgo);

        // 3. 가져온 데이터를 Map에 덮어쓰기
        for (WaitingStatProjection stat : dbStats) {
            String dayName = DayOfWeek.of(stat.getDayOfWeek()).name(); // 1=월요일 ~ 7=일요일
            int hour = stat.getHourOfDay();
            int avgTeams = (int) Math.round(stat.getAvgTeams());
            int avgWaitMin = (int) Math.round(stat.getAvgWaitMin());

            // 해당 요일/시간대의 0 데이터를 실제 값으로 교체
            statsMap.get(dayName).set(hour, new HourlyStatVo(hour, avgTeams, avgWaitMin));
        }

        // 4. Store 엔티티 업데이트 (DB에 JSON 저장)
        store.updateWeeklyStats(statsMap);
        log.info("✅ 식당 ID {} - 식당별 통계 갱신 완료", storeId);
    }
}