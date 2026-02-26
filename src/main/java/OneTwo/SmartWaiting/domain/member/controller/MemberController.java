package OneTwo.SmartWaiting.domain.member.controller;

import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberSignUpRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.requestDto.PasswordUpdateRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.responseDto.MemberResponseDto;
import OneTwo.SmartWaiting.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@Tag(name = "2. 회원(Member) API", description = "회원 정보 조회, 수정, 탈퇴 기능")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 상세 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<MemberResponseDto> getMyInfo(Principal principal) {
        return ResponseEntity.ok(memberService.getMyInfo(principal.getName()));
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인한 사용자의 이름(닉네임)을 수정합니다.")
    @PutMapping("/me")
    public ResponseEntity<Void> updateMember(
            Principal principal,
            @RequestBody @Valid MemberUpdateRequestDto request) {
        memberService.updateMember(principal.getName(), request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            Principal principal,
            @RequestBody @Valid PasswordUpdateRequestDto request) {
        memberService.updatePassword(principal.getName(), request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자를 탈퇴 처리합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMember(Principal principal) {
        memberService.deleteMember(principal.getName());
        return ResponseEntity.noContent().build();
    }

}
