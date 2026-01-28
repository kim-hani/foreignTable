package OneTwo.SmartWaiting.domain.member.service;

import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberSignUpRequestDto;
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

    // [C] 회원가입
    @Transactional
    public Long signup(MemberSignUpRequestDto request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // [수정] 빌더 패턴 사용
        Member member = Member.builder()
                .email(request.email())
                .nickname(request.nickname())
                .role(UserRole.USER) // 기본 권한
                .build();

        return memberRepository.save(member).getId();
    }

    // [R] 회원 단건 조회
    public MemberResponseDto getMember(Long memberId) {
        Member member = findMemberByIdOrThrow(memberId);
        return MemberResponseDto.from(member); // 내부적으로 Builder 사용됨
    }

    private Member findMemberByIdOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. ID=" + memberId));
    }
}
