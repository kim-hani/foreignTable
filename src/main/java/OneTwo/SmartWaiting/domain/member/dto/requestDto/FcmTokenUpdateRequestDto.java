package OneTwo.SmartWaiting.domain.member.dto.requestDto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenUpdateRequestDto(
        @NotBlank(message = "FCM 토큰을 입력해주세요.")
        String fcmToken
) { }
