package OneTwo.SmartWaiting.auth.controller;

import OneTwo.SmartWaiting.auth.dto.requestDto.AdminSignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignInRequestDto;
import OneTwo.SmartWaiting.auth.dto.requestDto.SignUpRequestDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignInResponseDto;
import OneTwo.SmartWaiting.auth.dto.responseDto.SignUpResponseDto;
import OneTwo.SmartWaiting.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ===== 일반 회원 ======
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponseDto> signup(@RequestBody @Valid SignUpRequestDto request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<SignInResponseDto> signin(@RequestBody @Valid SignInRequestDto request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    // ===== 관리자 ======
    @PostMapping("/admin/signup")
    public ResponseEntity<SignUpResponseDto> signupAdmin(@RequestBody @Valid AdminSignUpRequestDto request) {
        return ResponseEntity.ok(authService.signupAdmin(request));
    }

    @PostMapping("/admin/signin")
    public ResponseEntity<SignInResponseDto> signinAdmin(@RequestBody @Valid SignInRequestDto request) {
        return ResponseEntity.ok(authService.signinAdmin(request));
    }
}
