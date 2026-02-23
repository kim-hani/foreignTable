package OneTwo.SmartWaiting.domain.member.dto.requestDto;

import jakarta.validation.constraints.NotBlank;

public record MemberUpdateRequestDto(
        @NotBlank(message=  "변경할 이름을 작성해주세요.")
        String name
) { }
