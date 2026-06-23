package OneTwo.SmartWaiting.domain.review.dto.requestDto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewCreateRequestDto(

        @NotNull(message = "방문 기록은 필수입니다.")
        Long waitingId,

        @NotBlank(message = "내용을 입력해주세요.")
        String content,

        @Min(1) @Max(5)
        int rating,

        @Size(max = 5, message = "이미지는 최대 5장까지 첨부 가능합니다.")
        List<String> imageUrls
) {
}
