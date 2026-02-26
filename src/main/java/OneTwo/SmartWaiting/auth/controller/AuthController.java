package OneTwo.SmartWaiting.auth.controller;

import OneTwo.SmartWaiting.auth.dto.requestDto.AdminSignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignInRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignInResponseDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignUpResponseDto;
import OneTwo.SmartWaiting.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "1. 인증(Auth) API", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 기능")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ===== 일반 회원 ======
    @Operation(summary = "일반 회원가입", description = "일반 사용자로 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public ResponseEntity<SignInResponseDto> signup(@RequestBody @Valid SignUpRequestDto request) {
        return ResponseEntity.ok(authService.signupMember(request));
    }

    @Operation(summary = "일반 로그인", description = "일반 사용자 로그인을 진행하고 토큰을 발급받습니다.")
    @PostMapping("/signin")
    public ResponseEntity<SignInResponseDto> signin(@RequestBody @Valid SignInRequestDto request) {
        return ResponseEntity.ok(authService.signinMember(request));
    }

    // ===== 사장님 ======
    @Operation(summary = "사장님 회원가입", description = "식당 사장님 권한(OWNER)으로 회원가입을 진행합니다.")
    @PostMapping("/owner/signup")
    public ResponseEntity<SignInResponseDto> signupOwner(@RequestBody @Valid SignUpRequestDto request) {
        return ResponseEntity.ok(authService.signupOwner(request));
    }

    // ===== 관리자 ======
    @Operation(summary = "관리자 회원가입", description = "관리자 인증 키를 사용하여 관리자(ADMIN)로 회원가입을 진행합니다.")
    @PostMapping("/admin/signup")
    public ResponseEntity<SignUpResponseDto> signupAdmin(@RequestBody @Valid AdminSignUpRequestDto request) {
        return ResponseEntity.ok(authService.signupAdmin(request));
    }

    @Operation(summary = "관리자 로그인", description = "관리자 권한 확인 후 로그인을 진행합니다.")
    @PostMapping("/admin/signin")
    public ResponseEntity<SignInResponseDto> signinAdmin(@RequestBody @Valid SignInRequestDto request) {
        return ResponseEntity.ok(authService.signinAdmin(request));
    }

    // ===== 토큰 재발급 (Access & Refresh Token) ======
    @Operation(summary = "토큰 재발급", description = "만료된 Access Token을 Refresh Token을 이용해 재발급 받습니다.")
    @PostMapping("/reissue")
    public ResponseEntity<SignInResponseDto> reissueToken(
            @RequestBody @Valid OneTwo.SmartWaiting.auth.dto.requestDto.ReissueRequestDto request) {

        // AuthService에 만들어둔 reissueToken 메서드 호출
        return ResponseEntity.ok(authService.reissueToken(request));
    }

    // ===== 로그아웃 ======
    @Operation(summary = "로그아웃", description = "사용자의 Refresh Token을 삭제하여 로그아웃 처리합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        authService.logout(principal.getName());
        return ResponseEntity.ok().build();
    }
}
