package OneTwo.SmartWaiting.domain.waiting.dto.requestDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record WaitingRegisterRequestDto(
        @NotNull(message = "식당 ID는 필수입니다.")
        Long storeId,

        @Min(value =1 , message = "인원은 최소 1명 이상이어야 합니다.")
        Integer headCount
) { }
