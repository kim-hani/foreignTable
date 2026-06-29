package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreAnalyticsResponse;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAnalyticsServiceTest {

    @InjectMocks
    private StoreAnalyticsService storeAnalyticsService;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WaitingRepository waitingRepository;

    private static final String OWNER_EMAIL = "owner@gmail.com";
    private static final Long OWNER_ID = 1L;
    private static final Long STORE_ID = 10L;

    // ================= [ 성공 케이스 ] =================

    @Test
    @DisplayName("영업 분석 조회 성공 - 7일 기준 각 지표를 집계한다.")
    void getAnalytics_Success_7days() {
        // given
        mockOwnedStore();

        // 상태별 건수: WAITING 2, SEATED 5, NOSHOW 2, CANCEL 1 => 총 10건
        when(waitingRepository.findStatusCountsSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{WaitingStatus.WAITING, 2L},
                        new Object[]{WaitingStatus.SEATED, 5L},
                        new Object[]{WaitingStatus.NOSHOW, 2L},
                        new Object[]{WaitingStatus.CANCEL, 1L}
                ));
        when(waitingRepository.findAverageWaitMinutesSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(12.5);
        when(waitingRepository.findTop3BusyHoursSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{12, 5L},
                        new Object[]{18, 3L},
                        new Object[]{13, 2L}
                ));
        when(waitingRepository.findHeadCountDistributionSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{2, 6L},
                        new Object[]{4, 4L}
                ));

        // when
        StoreAnalyticsResponse response = storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 7);

        // then
        assertThat(response.days()).isEqualTo(7);
        assertThat(response.totalWaitings()).isEqualTo(10L);
        assertThat(response.averageWaitMinutes()).isEqualTo(12.5);
        // 노쇼율 = NOSHOW(2) / (SEATED 5 + NOSHOW 2 + CANCEL 1 = 8) * 100 = 25.0
        assertThat(response.noshowRate()).isEqualTo(25.0);
        assertThat(response.top3BusyHours()).hasSize(3);
        assertThat(response.top3BusyHours().get(0).hour()).isEqualTo(12);
        assertThat(response.top3BusyHours().get(0).count()).isEqualTo(5L);
        assertThat(response.headCountDistribution())
                .containsEntry(2, 6L)
                .containsEntry(4, 4L);
    }

    @Test
    @DisplayName("영업 분석 조회 성공 - days=30 파라미터가 응답에 반영된다.")
    void getAnalytics_Success_30days() {
        // given
        mockOwnedStore();
        when(waitingRepository.findStatusCountsSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{WaitingStatus.SEATED, 3L}));
        when(waitingRepository.findAverageWaitMinutesSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(8.0);
        when(waitingRepository.findTop3BusyHoursSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(waitingRepository.findHeadCountDistributionSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        StoreAnalyticsResponse response = storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 30);

        // then
        assertThat(response.days()).isEqualTo(30);
        assertThat(response.totalWaitings()).isEqualTo(3L);
    }

    // ================= [ 경계값 케이스 ] =================

    @Test
    @DisplayName("영업 분석 조회 - SEATED 기록이 없으면 averageWaitMinutes는 null이다.")
    void getAnalytics_NoSeated_AverageNull() {
        // given
        mockOwnedStore();
        when(waitingRepository.findStatusCountsSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{WaitingStatus.WAITING, 2L}));
        when(waitingRepository.findAverageWaitMinutesSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(null);
        when(waitingRepository.findTop3BusyHoursSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(waitingRepository.findHeadCountDistributionSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        StoreAnalyticsResponse response = storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 7);

        // then
        assertThat(response.averageWaitMinutes()).isNull();
    }

    @Test
    @DisplayName("영업 분석 조회 - 종료된 웨이팅이 없으면 noshowRate는 null이다.")
    void getAnalytics_NoTerminal_NoshowRateNull() {
        // given
        mockOwnedStore();
        // WAITING/CALL만 존재 => 종료(terminal) 건수 0
        when(waitingRepository.findStatusCountsSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{WaitingStatus.WAITING, 3L},
                        new Object[]{WaitingStatus.CALL, 1L}
                ));
        when(waitingRepository.findAverageWaitMinutesSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(null);
        when(waitingRepository.findTop3BusyHoursSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(waitingRepository.findHeadCountDistributionSince(eq(STORE_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        StoreAnalyticsResponse response = storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 7);

        // then
        assertThat(response.noshowRate()).isNull();
        assertThat(response.totalWaitings()).isEqualTo(4L);
    }

    // ================= [ 실패 케이스 ] =================

    @Test
    @DisplayName("영업 분석 조회 실패 - 존재하지 않는 가게면 STORE_NOT_FOUND 예외가 발생한다.")
    void getAnalytics_Fail_StoreNotFound() {
        // given
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 7));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("영업 분석 조회 실패 - 본인 가게가 아니면 UNAUTHORIZED_STORE_OWNER 예외가 발생한다.")
    void getAnalytics_Fail_NotOwner() {
        // given
        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(OWNER_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(mockStore));

        Member otherMember = mock(Member.class);
        when(otherMember.getId()).thenReturn(999L); // 다른 회원
        when(memberRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(otherMember));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> storeAnalyticsService.getAnalytics(STORE_ID, OWNER_EMAIL, 7));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    // ================= [ Helper ] =================

    private void mockOwnedStore() {
        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getOwnerId()).thenReturn(OWNER_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(mockStore));

        Member mockOwner = mock(Member.class);
        lenient().when(mockOwner.getId()).thenReturn(OWNER_ID);
        when(memberRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(mockOwner));
    }
}
