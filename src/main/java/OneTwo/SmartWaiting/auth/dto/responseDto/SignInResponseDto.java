package OneTwo.SmartWaiting.auth.dto.responseDto;

import lombok.Builder;

@Builder
public record SignInResponseDto(
        String grantType,
        String accessToken,
        String refreshToken,
        Long accessTokenExpiresIn
) { }
