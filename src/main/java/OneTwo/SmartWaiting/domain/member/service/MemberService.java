package OneTwo.SmartWaiting.domain.member.service;

import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberSignUpRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.responseDto.MemberResponseDto;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    // 이메일로 회원 정보 조회
    public MemberResponseDto getMyInfo(String email){
        Member member = findMemberByEmailOrThrow(email);
        return MemberResponseDto.from(member);
    }

    // [R] 회원 단건 조회
    public MemberResponseDto getMember(Long memberId) {
        Member member = findMemberByIdOrThrow(memberId);
        return MemberResponseDto.from(member); // 내부적으로 Builder 사용됨
    }

    @Transactional
    public void updateMember(String email, MemberUpdateRequestDto requestDto) {
        Member member = findMemberByEmailOrThrow(email);

        if(requestDto.name() != null && !requestDto.name().isBlank()) {
            member.updateNickname(requestDto.name());
        }
    }

    @Transactional
    public void deleteMember(String email) {
        Member member = findMemberByEmailOrThrow(email);
        member.softDelete();
    }

    private Member findMemberByIdOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. ID=" + memberId));
    }

    private Member findMemberByEmailOrThrow(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. Email=" + email));
    }
}
