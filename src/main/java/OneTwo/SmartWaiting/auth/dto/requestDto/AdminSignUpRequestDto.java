package OneTwo.SmartWaiting.auth.dto.requestDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminSignUpRequestDto(
        @NotBlank(message = "관리자 아이디는 필수입니다.") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        @NotBlank(message = "이메일은 필수입니다.") @Email String email,
        @NotBlank(message = "닉네임은 필수입니다.") String nickname,
        @NotBlank(message = "관리자 인증 키는 필수입니다.") String adminKey // 필수 필드
) { }