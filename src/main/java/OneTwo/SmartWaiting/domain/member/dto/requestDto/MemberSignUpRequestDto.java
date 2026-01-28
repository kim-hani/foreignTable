package OneTwo.SmartWaiting.domain.member.dto.requestDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MemberSignUpRequestDto(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "유효한 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname
)
{ }
