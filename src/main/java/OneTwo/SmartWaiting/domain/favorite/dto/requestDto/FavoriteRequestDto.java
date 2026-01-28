package OneTwo.SmartWaiting.domain.favorite.dto.requestDto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record FavoriteRequestDto(
        @NotNull(message = "가게 ID는 필수입니다.")
        Long storeId,

        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId
) {}
