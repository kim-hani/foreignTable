package OneTwo.SmartWaiting.domain.support.dto;

import jakarta.validation.constraints.NotBlank;

public record SupportManualCreateRequestDto(

        @NotBlank(message = "카테고리는 필수입니다.")
        String category,

        @NotBlank(message = "질문은 필수입니다.")
        String question,

        @NotBlank(message = "답변은 필수입니다.")
        String answer,

        String keywords
) {
}
