package OneTwo.SmartWaiting.domain.member.service;

import OneTwo.SmartWaiting.auth.repository.RefreshTokenRepository;
import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberSignUpRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.PasswordUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.responseDto.MemberResponseDto;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.parameters.P;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    // 이메일로 회원 정보 조회
    public MemberResponseDto getMyInfo(String email){
        Member member = findMemberByEmailOrThrow(email);
        return MemberResponseDto.from(member);
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

        member.withdraw();
        refreshTokenRepository.deleteById(String.valueOf(member.getId()));
    }

    @Transactional
    public void updatePassword(String email, PasswordUpdateRequestDto requestDto) {
        if (!requestDto.newPassword().equals(requestDto.newPasswordCheck())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        Member member = findMemberByEmailOrThrow(email);

        if (!passwordEncoder.matches(requestDto.currentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        member.updatePassword(passwordEncoder.encode(requestDto.newPassword()));
    }

    private Member findMemberByIdOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member findMemberByEmailOrThrow(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(member.getIsDeleted() == Boolean.TRUE) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }
}
