package OneTwo.SmartWaiting.auth.service;

import OneTwo.SmartWaiting.auth.dto.requestDto.SignInRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignInResponseDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignUpResponseDto;
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
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${admin.secret-key}")
    private String adminSecretKey;


    @Transactional
    public SignUpResponseDto signup(SignUpRequestDto requestDto) {
        if(memberRepository.existsByEmail(requestDto.email()))
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");

        if(memberRepository.findByLoginId(requestDto.loginId()).isPresent())
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");

        UserRole role = UserRole.USER;

        if (requestDto.adminKey() != null && !requestDto.adminKey().isEmpty()) {
            if (requestDto.adminKey().equals(adminSecretKey)) {
                role = UserRole.ADMIN;
            } else {
                throw new IllegalArgumentException("관리자 암호가 일치하지 않습니다.");
            }
        }

        Member member = Member.builder()
                .loginId(requestDto.loginId())
                .password(passwordEncoder.encode(requestDto.password()))
                .email(requestDto.email())
                .nickname(requestDto.nickname())
                .role(UserRole.USER)
                .provider("general")
                .build();

        Member savedMember = memberRepository.save(member);
        return SignUpResponseDto.from(savedMember);
    }

    @Transactional
    public SignInResponseDto signin(SignInRequestDto requestDto) {
        Member member = memberRepository.findByLoginId(requestDto.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if(!passwordEncoder.matches(requestDto.password(),member.getPassword()))
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");

        String accessToken = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken();

        refreshTokenService.saveOrUpdate(String.valueOf(member.getId()), refreshToken);

        return SignInResponseDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(1800000L) // 30분
                .build();
    }


}
