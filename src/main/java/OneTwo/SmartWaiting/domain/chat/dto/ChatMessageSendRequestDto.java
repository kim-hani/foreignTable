package OneTwo.SmartWaiting.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequestDto(
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 1000, message = "메시지는 1000자를 초과할 수 없습니다.")
        String content
) {
}
