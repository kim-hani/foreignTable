package OneTwo.SmartWaiting.domain.member.service;

import OneTwo.SmartWaiting.auth.repository.RefreshTokenRepository;
import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.PasswordUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.responseDto.MemberResponseDto;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("실패 - 존재하지 않는 이메일로 요청 시 에러 발생")
    void findMember_Fail_NotFound(){
        // given
        String email = "test@gmail.com";
        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.getMyInfo(email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("실패 - 이미 탈퇴한 회원일 경우 에러 발생")
    void findMember_Fail_AlreadyDeleted(){

        // given
        String email = "test@gmail.com";
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.getMyInfo(email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_Success() {
        // given
        String email = "test@gmail.com";
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(false);
        when(mockMember.getId()).thenReturn(1L);

        // when
        MemberResponseDto result = memberService.getMyInfo(email);

        // then
        assertThat(result).isNotNull();
    }


    @Test
    @DisplayName("회원 닉네임 수정 성공")
    void updateMember_Success() {
        // given
        String email = "test@gmail.com";
        MemberUpdateRequestDto requestDto = new MemberUpdateRequestDto("새로운닉네임");
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(false);

        // when
        memberService.updateMember(email, requestDto);

        // then
        verify(mockMember, times(1)).updateNickname("새로운닉네임");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 상태 변경 및 리프레시 토큰 삭제")
    void deleteMember_Success() {
        // given
        String email = "test@gmail.com";
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(false);
        when(mockMember.getId()).thenReturn(100L);

        // when
        memberService.deleteMember(email);

        // then
        verify(mockMember, times(1)).withdraw();
        verify(refreshTokenRepository, times(1)).deleteById("100");
    }


    @Test
    @DisplayName("비밀번호 변경 실패 - 새 비밀번호와 확인 비밀번호가 다름")
    void updatePassword_Fail_NewPasswordMismatch() {
        // given
        String email = "test@gmail.com";
        PasswordUpdateRequestDto requestDto = new PasswordUpdateRequestDto("oldPass", "newPass", "diffPass");

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.updatePassword(email, requestDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호가 일치하지 않음")
    void updatePassword_Fail_CurrentPasswordMismatch() {
        // given
        String email = "test@gmail.com";
        PasswordUpdateRequestDto requestDto = new PasswordUpdateRequestDto("wrongOld", "newPass", "newPass");
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(false);
        when(mockMember.getPassword()).thenReturn("encodedOldPass");
        when(passwordEncoder.matches("wrongOld", "encodedOldPass")).thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.updatePassword(email, requestDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePassword_Success() {
        // given
        String email = "test@gmail.com";
        PasswordUpdateRequestDto requestDto = new PasswordUpdateRequestDto("oldPass", "newPass", "newPass");
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(mockMember.getIsDeleted()).thenReturn(false);
        when(mockMember.getPassword()).thenReturn("encodedOldPass");

        when(passwordEncoder.matches("oldPass", "encodedOldPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        // when
        memberService.updatePassword(email, requestDto);

        // then
        verify(mockMember, times(1)).updatePassword("encodedNewPass");
    }
}