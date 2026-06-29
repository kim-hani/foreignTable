package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreAnalyticsResponse;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreAnalyticsResponse.BusyHourEntry;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사장님(OWNER) 영업 분석 대시보드 서비스(#10).
 * 본인 가게에 한해 기간별 웨이팅 운영 지표를 집계한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAnalyticsService {

    // 노쇼율 분모: 정상 종료(SEATED) + 노쇼(NOSHOW) + 취소(CANCEL)
    private static final List<WaitingStatus> TERMINAL_STATUSES =
            List.of(WaitingStatus.SEATED, WaitingStatus.NOSHOW, WaitingStatus.CANCEL);

    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final WaitingRepository waitingRepository;

    public StoreAnalyticsResponse getAnalytics(Long storeId, String email, int days) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        validateOwner(store, email);

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        Map<WaitingStatus, Long> statusCounts = parseStatusCounts(
                waitingRepository.findStatusCountsSince(storeId, since));

        long totalWaitings = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        Double averageWaitMinutes = waitingRepository.findAverageWaitMinutesSince(storeId, since);
        Double noshowRate = calculateNoshowRate(statusCounts);
        List<BusyHourEntry> top3BusyHours = mapTop3BusyHours(
                waitingRepository.findTop3BusyHoursSince(storeId, since));
        Map<Integer, Long> headCountDistribution = mapHeadCountDistribution(
                waitingRepository.findHeadCountDistributionSince(storeId, since));

        return new StoreAnalyticsResponse(
                days, totalWaitings, averageWaitMinutes, noshowRate, top3BusyHours, headCountDistribution);
    }

    private void validateOwner(Store store, String email) {
        Member requester = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!store.getOwnerId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
        }
    }

    private Map<WaitingStatus, Long> parseStatusCounts(List<Object[]> rows) {
        Map<WaitingStatus, Long> counts = new EnumMap<>(WaitingStatus.class);
        for (Object[] row : rows) {
            counts.put((WaitingStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Double calculateNoshowRate(Map<WaitingStatus, Long> statusCounts) {
        long terminalCount = TERMINAL_STATUSES.stream()
                .mapToLong(status -> statusCounts.getOrDefault(status, 0L))
                .sum();
        if (terminalCount == 0) {
            return null;
        }
        long noshowCount = statusCounts.getOrDefault(WaitingStatus.NOSHOW, 0L);
        return noshowCount * 100.0 / terminalCount;
    }

    private List<BusyHourEntry> mapTop3BusyHours(List<Object[]> rows) {
        List<BusyHourEntry> entries = new ArrayList<>();
        for (Object[] row : rows) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            entries.add(new BusyHourEntry(hour, count));
        }
        return entries;
    }

    private Map<Integer, Long> mapHeadCountDistribution(List<Object[]> rows) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (Object[] row : rows) {
            distribution.put(((Number) row[0]).intValue(), (Long) row[1]);
        }
        return distribution;
    }
}
