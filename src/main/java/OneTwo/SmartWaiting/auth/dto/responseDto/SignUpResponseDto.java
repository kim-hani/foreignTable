package OneTwo.SmartWaiting.auth.dto.responseDto;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import lombok.Builder;

@Builder
public record SignUpResponseDto(
        Long id,
        String email,
        String nickname
) {
    public static SignUpResponseDto from(Member member) {
        return SignUpResponseDto.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .build();
    }
}
