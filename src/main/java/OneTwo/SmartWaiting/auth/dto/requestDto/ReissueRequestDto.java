package OneTwo.SmartWaiting.auth.dto.requestDto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequestDto(
     @NotBlank(message = "Refresh Token은 필수입니다.")
     String refreshToken
) { }
