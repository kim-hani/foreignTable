package OneTwo.SmartWaiting.auth.service;

import OneTwo.SmartWaiting.auth.dto.requestDto.AdminSignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.ReissueRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignInRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignInResponseDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignUpResponseDto;
import OneTwo.SmartWaiting.auth.entity.RefreshToken;
import OneTwo.SmartWaiting.auth.repository.RefreshTokenRepository;
import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.config.JwtTokenProvider;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private final String TEST_ADMIN_KEY = "testAdminKey";
    private final String TEST_ACCESS_TOKEN = "testAccessToken";
    private final String TEST_REFRESH_TOKEN = "testRefreshToken";

    @BeforeEach
    void setUp() {
        // @Value 주입을 위한 ReflectionTestUtils 사용
        ReflectionTestUtils.setField(authService, "adminSecretKey", TEST_ADMIN_KEY);

        // JWT 발급 공통 Mock 세팅 (NPE 방지)
        lenient().when(jwtTokenProvider.createAccessToken(anyString(), anyString())).thenReturn(TEST_ACCESS_TOKEN);
        lenient().when(jwtTokenProvider.createRefreshToken()).thenReturn(TEST_REFRESH_TOKEN);
    }

    // ================= [ 일반 사용자 회원가입 ] =================

    @Test
    @DisplayName("성공 - 일반 사용자 회원가입")
    void signupMember_Success() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("user@gmail.com", "user123", "pass123", "pass123", "일반유저");
        Member mockMember = createMockMember(1L, requestDto.email(), requestDto.loginId(), "encodedPass", requestDto.nickname(), UserRole.USER, false);

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(requestDto.password())).thenReturn("encodedPass");
        when(memberRepository.save(any(Member.class))).thenReturn(mockMember);

        // when
        SignInResponseDto result = authService.signupMember(requestDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
        verify(memberRepository, times(1)).save(any(Member.class));
        verify(refreshTokenService, times(1)).saveOrUpdate(String.valueOf(mockMember.getId()), TEST_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("실패 - 일반 사용자 회원가입: 이메일 중복")
    void signupMember_Fail_EmailAlreadyExists() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("dup@gmail.com", "user123", "pass123", "pass123", "유저");
        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signupMember(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("실패 - 일반 사용자 회원가입: 아이디 중복")
    void signupMember_Fail_LoginIdAlreadyExists() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("user@gmail.com", "dupId", "pass123", "pass123", "유저");

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.of(mock(Member.class)));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signupMember(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("실패 - 일반 사용자 회원가입: 비밀번호 확인 불일치")
    void signupMember_Fail_PasswordMismatch() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("user@gmail.com", "user123", "pass123", "wrongPass", "유저");

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signupMember(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    // ================= [ 사장님 회원가입 ] =================

    @Test
    @DisplayName("성공 - 사장님 회원가입")
    void signupOwner_Success() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("owner@gmail.com", "owner123", "pass123", "pass123", "사장님");
        Member mockOwner = createMockMember(2L, requestDto.email(), requestDto.loginId(), "encodedPass", requestDto.nickname(), UserRole.OWNER, false);

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(requestDto.password())).thenReturn("encodedPass");
        when(memberRepository.save(any(Member.class))).thenReturn(mockOwner);

        // when
        SignInResponseDto result = authService.signupOwner(requestDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
        verify(memberRepository, times(1)).save(any(Member.class));
    }

    @Test
    @DisplayName("실패 - 사장님 회원가입: 비밀번호 확인 불일치")
    void signupOwner_Fail_PasswordMismatch() {
        // given
        SignUpRequestDto requestDto = createSignUpRequestDto("owner@gmail.com", "owner123", "pass123", "wrongPass", "사장님");

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signupOwner(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    // ================= [ 관리자 회원가입 ] =================

    @Test
    @DisplayName("성공 - 관리자 회원가입")
    void signupAdmin_Success() {
        // given
        AdminSignUpRequestDto requestDto = createAdminSignUpRequestDto("admin@gmail.com", "admin123", "pass123", "관리자", TEST_ADMIN_KEY);
        Member mockAdmin = createMockMember(3L, requestDto.email(), requestDto.loginId(), "encodedPass", requestDto.nickname(), UserRole.ADMIN, false);

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(requestDto.password())).thenReturn("encodedPass");
        when(memberRepository.save(any(Member.class))).thenReturn(mockAdmin);

        // when
        SignUpResponseDto result = authService.signupAdmin(requestDto);

        // then
        assertThat(result).isNotNull();
        verify(memberRepository, times(1)).save(any(Member.class));
    }

    @Test
    @DisplayName("실패 - 관리자 회원가입: 관리자 시크릿 키 불일치")
    void signupAdmin_Fail_InvalidAdminKey() {
        // given
        AdminSignUpRequestDto requestDto = createAdminSignUpRequestDto("admin@gmail.com", "admin123", "pass123", "관리자", "wrongAdminKey");

        when(memberRepository.existsByEmail(requestDto.email())).thenReturn(false);
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signupAdmin(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_KEY);
    }

    // ================= [ 로그인 (일반 / 관리자) ] =================

    @Test
    @DisplayName("성공 - 회원 로그인")
    void signinMember_Success() {
        // given
        SignInRequestDto requestDto = createSignInRequestDto("user123", "pass123");
        Member mockMember = createMockMember(1L, "user@gmail.com", requestDto.loginId(), "encodedPass", "유저", UserRole.USER, false);

        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.of(mockMember));
        when(passwordEncoder.matches(requestDto.password(), mockMember.getPassword())).thenReturn(true);

        // when
        SignInResponseDto result = authService.signinMember(requestDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("실패 - 로그인: 아이디 존재하지 않음")
    void signinMember_Fail_LoginIdNotFound() {
        // given
        SignInRequestDto requestDto = createSignInRequestDto("wrongId", "pass123");
        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signinMember(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IDPASSWORD);
    }

    @Test
    @DisplayName("실패 - 로그인: 비밀번호 불일치")
    void signinMember_Fail_PasswordMismatch() {
        // given
        SignInRequestDto requestDto = createSignInRequestDto("user123", "wrongPass");
        Member mockMember = createMockMember(1L, "user@gmail.com", requestDto.loginId(), "encodedPass", "유저", UserRole.USER, false);

        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.of(mockMember));
        when(passwordEncoder.matches(requestDto.password(), mockMember.getPassword())).thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signinMember(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IDPASSWORD);
    }

    @Test
    @DisplayName("성공 - 관리자 로그인")
    void signinAdmin_Success() {
        // given
        SignInRequestDto requestDto = createSignInRequestDto("admin123", "pass123");
        Member mockAdmin = createMockMember(3L, "admin@gmail.com", requestDto.loginId(), "encodedPass", "관리자", UserRole.ADMIN, false);

        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.of(mockAdmin));
        when(passwordEncoder.matches(requestDto.password(), mockAdmin.getPassword())).thenReturn(true);

        // when
        SignInResponseDto result = authService.signinAdmin(requestDto);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("실패 - 관리자 로그인: 관리자 권한이 아닌 계정으로 접근")
    void signinAdmin_Fail_AccessDenied() {
        // given
        SignInRequestDto requestDto = createSignInRequestDto("user123", "pass123");
        // 일반 USER 권한의 회원 생성
        Member mockUser = createMockMember(1L, "user@gmail.com", requestDto.loginId(), "encodedPass", "일반유저", UserRole.USER, false);

        when(memberRepository.findByLoginId(requestDto.loginId())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(requestDto.password(), mockUser.getPassword())).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signinAdmin(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    // ================= [ 로그아웃 ] =================

    @Test
    @DisplayName("성공 - 로그아웃")
    void logout_Success() {
        // given
        String email = "test@gmail.com";
        Member mockMember = createMockMember(1L, email, "user123", "pass", "유저", UserRole.USER, false);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));

        // when
        authService.logout(email);

        // then
        verify(refreshTokenRepository, times(1)).deleteById(String.valueOf(mockMember.getId()));
    }

    @Test
    @DisplayName("실패 - 로그아웃: 존재하지 않는 회원")
    void logout_Fail_MemberNotFound() {
        // given
        String email = "none@gmail.com";
        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ================= [ 토큰 재발급 (Reissue) ] =================

    @Test
    @DisplayName("성공 - JWT 토큰 재발급")
    void reissueToken_Success() {
        // given
        String refreshTokenValue = "validRefreshToken";
        ReissueRequestDto requestDto = createReissueRequestDto(refreshTokenValue);

        Member mockMember = createMockMember(1L, "user@gmail.com", "user123", "pass", "유저", UserRole.USER, false);
        RefreshToken mockRefreshToken = createMockRefreshToken(String.valueOf(mockMember.getId()), refreshTokenValue);

        when(jwtTokenProvider.validateToken(refreshTokenValue)).thenReturn(true);
        when(refreshTokenRepository.findByValue(refreshTokenValue)).thenReturn(Optional.of(mockRefreshToken));
        when(memberRepository.findById(mockMember.getId())).thenReturn(Optional.of(mockMember));

        // when
        SignInResponseDto result = authService.reissueToken(requestDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
        verify(refreshTokenService, times(1)).saveOrUpdate(String.valueOf(mockMember.getId()), TEST_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("실패 - 토큰 재발급: 유효하지 않은 Refresh Token (검증 실패)")
    void reissueToken_Fail_InvalidToken_Provider() {
        // given
        ReissueRequestDto requestDto = createReissueRequestDto("invalidToken");
        when(jwtTokenProvider.validateToken(requestDto.refreshToken())).thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissueToken(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("실패 - 토큰 재발급: DB에 존재하지 않는 Refresh Token")
    void reissueToken_Fail_InvalidToken_NotFoundInDB() {
        // given
        ReissueRequestDto requestDto = createReissueRequestDto("nonExistentToken");

        when(jwtTokenProvider.validateToken(requestDto.refreshToken())).thenReturn(true);
        when(refreshTokenRepository.findByValue(requestDto.refreshToken())).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissueToken(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("실패 - 토큰 재발급: 이미 탈퇴한 회원 (Soft Delete 상태)")
    void reissueToken_Fail_MemberAlreadyDeleted() {
        // given
        String refreshTokenValue = "validRefreshToken";
        ReissueRequestDto requestDto = createReissueRequestDto(refreshTokenValue);

        // isDeleted = true 인 회원 생성
        Member mockDeletedMember = createMockMember(1L, "del@gmail.com", "del123", "pass", "탈퇴회원", UserRole.USER, true);
        RefreshToken mockRefreshToken = createMockRefreshToken(String.valueOf(mockDeletedMember.getId()), refreshTokenValue);

        when(jwtTokenProvider.validateToken(refreshTokenValue)).thenReturn(true);
        when(refreshTokenRepository.findByValue(refreshTokenValue)).thenReturn(Optional.of(mockRefreshToken));
        when(memberRepository.findById(mockDeletedMember.getId())).thenReturn(Optional.of(mockDeletedMember));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissueToken(requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ================= [ Helper Methods ] =================

    private SignUpRequestDto createSignUpRequestDto(String email, String loginId, String password, String passwordCheck, String nickname) {
        return new SignUpRequestDto(loginId, password, passwordCheck, email, nickname, null);
    }

    private AdminSignUpRequestDto createAdminSignUpRequestDto(String email, String loginId, String password, String nickname, String adminKey) {
        return new AdminSignUpRequestDto(email, loginId, password, nickname, adminKey);
    }

    private SignInRequestDto createSignInRequestDto(String loginId, String password) {
        return new SignInRequestDto(loginId, password);
    }

    private ReissueRequestDto createReissueRequestDto(String refreshToken) {
        return new ReissueRequestDto(refreshToken);
    }

    private Member createMockMember(Long id, String email, String loginId, String password, String nickname, UserRole role, Boolean isDeleted) {
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getEmail()).thenReturn(email);
        lenient().when(member.getLoginId()).thenReturn(loginId);
        lenient().when(member.getPassword()).thenReturn(password);
        lenient().when(member.getNickname()).thenReturn(nickname);
        lenient().when(member.getRole()).thenReturn(role);
        lenient().when(member.getIsDeleted()).thenReturn(isDeleted);
        return member;
    }

    private RefreshToken createMockRefreshToken(String key, String value) {
        RefreshToken refreshToken = mock(RefreshToken.class);
        lenient().when(refreshToken.getKey()).thenReturn(key);
        lenient().when(refreshToken.getValue()).thenReturn(value);
        return refreshToken;
    }
}