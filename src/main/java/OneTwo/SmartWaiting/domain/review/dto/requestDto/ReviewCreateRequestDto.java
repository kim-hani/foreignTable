package OneTwo.SmartWaiting.domain.review.dto.requestDto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewCreateRequestDto(
        @NotNull Long storeId,
        @NotBlank(message = "내용을 입력해주세요.")
        String content,

        @Min(1) @Max(5)
        int rating
) {
}
