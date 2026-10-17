package OneTwo.SmartWaiting.domain.waiting.entity;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitingTest {

    private Waiting waitingWithPostponedCount(int count) {
        return Waiting.builder()
                .headCount(2)
                .status(WaitingStatus.WAITING)
                .queueNumber(1)
                .postponedCount(count)
                .ticketTime(LocalDateTime.of(2026, 10, 16, 12, 0))
                .build();
    }

    @Test
    @DisplayName("미루기 성공 - 횟수가 1 증가하고 발권 시각이 현재로 갱신되어 맨 뒤로 이동한다.")
    void postpone_UnderLimit_IncrementsAndMovesToBack() {
        Waiting waiting = waitingWithPostponedCount(1);

        waiting.postpone();

        assertThat(waiting.getPostponedCount()).isEqualTo(2);
        assertThat(waiting.getTicketTime()).isAfter(LocalDateTime.of(2026, 10, 16, 12, 0));
    }

    @Test
    @DisplayName("미루기 실패 - 2회를 넘기면 500이 아닌 POSTPONE_LIMIT_EXCEEDED 비즈니스 예외가 발생한다.")
    void postpone_OverLimit_ThrowsBusinessException() {
        Waiting waiting = waitingWithPostponedCount(2);

        assertThatThrownBy(waiting::postpone)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POSTPONE_LIMIT_EXCEEDED);
    }
}
