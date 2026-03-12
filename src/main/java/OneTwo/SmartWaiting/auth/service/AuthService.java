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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${admin.secret-key}")
    private String adminSecretKey;

    // ====== 일반 사용자 로직 ======
    @Transactional
    public SignInResponseDto signupMember(SignUpRequestDto requestDto) {
        validation(requestDto.email(),requestDto.loginId());

        if(!requestDto.password().equals(requestDto.passwordCheck())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        Member member = Member.builder()
                .email(requestDto.email())
                .loginId(requestDto.loginId())
                .password(passwordEncoder.encode(requestDto.password()))
                .nickname(requestDto.nickname())
                .role(UserRole.USER)
                .provider("general")
                .build();

        return createTokenResponse(memberRepository.save(member));
    }

    @Transactional
    public SignInResponseDto signinMember(SignInRequestDto requestDto) {
       Member member = validateMember(requestDto.loginId(), requestDto.password());

       return createTokenResponse(member);
    }

    // ===== 사장님 회원가입 ======
    @Transactional
    public SignInResponseDto signupOwner(SignUpRequestDto requestDto) {
        validation(requestDto.email(),requestDto.loginId());

        if(!requestDto.password().equals(requestDto.passwordCheck())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        Member owner = Member.builder()
                .email(requestDto.email())
                .loginId(requestDto.loginId())
                .password(passwordEncoder.encode(requestDto.password()))
                .nickname(requestDto.nickname())
                .role(UserRole.OWNER)
                .provider("general")
                .build();

        return createTokenResponse(memberRepository.save(owner));
    }

    // ====== 관리자 로직 ======
    @Transactional
    public SignUpResponseDto signupAdmin(AdminSignUpRequestDto requestDto) {
        validation(requestDto.email(),requestDto.loginId());

        if(!requestDto.adminKey().equals(adminSecretKey)) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_KEY);
        }

        Member admin = Member.builder()
                .email(requestDto.email())
                .loginId(requestDto.loginId())
                .password(passwordEncoder.encode(requestDto.password()))
                .nickname(requestDto.nickname())
                .role(UserRole.ADMIN)
                .provider("general")
                .build();

        return SignUpResponseDto.from(memberRepository.save(admin));
    }

    @Transactional
    public SignInResponseDto signinAdmin(SignInRequestDto requestDto) {
        Member admin = validateMember(requestDto.loginId(), requestDto.password());

        if(admin.getRole() != UserRole.ADMIN ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return createTokenResponse(admin);
    }

    // 로그아웃
    @Transactional
    public void logout(String email){
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        refreshTokenRepository.deleteById(String.valueOf(member.getId()));
    }

    // ====== 공통 로직 ======
    private void validation(String email, String loginId) {
        if (memberRepository.existsByEmail(email))
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        if (memberRepository.findByLoginId(loginId).isPresent())
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
    }

    private Member validateMember(String loginId, String password) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_IDPASSWORD));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_IDPASSWORD);
        }

        return member;
    }

    private SignInResponseDto createTokenResponse(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken();

        refreshTokenService.saveOrUpdate(String.valueOf(member.getId()), refreshToken);

        return SignInResponseDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(1800000L)
                .build();
    }

    // ====== 토큰 재발급 (Reissue) ======
    @Transactional
    public SignInResponseDto reissueToken(ReissueRequestDto requestDto) {
        // 1. Refresh Token이 유효한지(만료되지 않았는지, 위조되지 않았는지) 검증
        if (!jwtTokenProvider.validateToken(requestDto.refreshToken())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 2. DB에서 넘어온 Refresh Token 값으로 저장된 토큰 엔티티 찾기
        RefreshToken savedToken = refreshTokenRepository.findByValue(requestDto.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        // 3. 찾은 토큰 엔티티의 ID(memberId)로 회원 정보 조회
        Long memberId = Long.parseLong(savedToken.getKey());
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(member.getIsDeleted() == Boolean.TRUE){
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // 4. 새로운 Access Token 및 Refresh Token 발급 (기존 createTokenResponse 재활용!)
        return createTokenResponse(member);
    }

}
