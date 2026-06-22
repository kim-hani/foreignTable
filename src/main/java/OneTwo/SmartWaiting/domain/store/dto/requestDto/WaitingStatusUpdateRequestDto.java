package OneTwo.SmartWaiting.domain.store.dto.requestDto;

import jakarta.validation.constraints.NotNull;

public record WaitingStatusUpdateRequestDto(
        @NotNull Boolean isAcceptingWaiting
) {}
