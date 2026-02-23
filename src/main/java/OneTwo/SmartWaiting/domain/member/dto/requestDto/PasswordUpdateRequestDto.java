package OneTwo.SmartWaiting.domain.member.dto.requestDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordUpdateRequestDto(
     @NotBlank(message = "현재 비밀번호를 입력해주세요.")
     String currentPassword,

     @NotBlank(message = "새 비밀번호를 입력해주세요.")
     @Pattern(
             regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*?_]).{8,16}$",
             message = "비밀번호는 영문자, 숫자, 특수문자를 포함하여 8~16자여야 합니다."
     )
     String newPassword,

     @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
     String newPasswordCheck
) { }
