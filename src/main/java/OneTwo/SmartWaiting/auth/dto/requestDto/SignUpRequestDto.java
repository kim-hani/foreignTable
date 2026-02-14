package OneTwo.SmartWaiting.auth.dto.requestDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignUpRequestDto(
        @NotBlank(message = " 아이디는 필수입니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*?_]).{8,16}$",
                message = "비밀번호는 영문자, 숫자, 특수문자를 포함하여 8~16자여야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인은 필수입니다.")
        String passwordCheck,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname,

        String adminKey
) {
}
