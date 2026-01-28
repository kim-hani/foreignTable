package OneTwo.SmartWaiting.domain.waiting.dto.requestDto;

import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import jakarta.validation.constraints.NotNull;

public record WaitingChangeRequestDto(
        @NotNull(message = "변경할 상태는 필수입니다.")
        WaitingStatus status
) { }
