package OneTwo.SmartWaiting.domain.member.dto.responseDto;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import lombok.Builder;

@Builder
public record MemberResponseDto(
        Long id,
        String email,
        String nickname,
        UserRole role
) {
    public static MemberResponseDto from(Member member) {
        return MemberResponseDto.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .build();
    }
}
