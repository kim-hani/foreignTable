package OneTwo.SmartWaiting.domain.member.controller;

import OneTwo.SmartWaiting.domain.member.dto.requestDto.MemberSignUpRequestDto;
import OneTwo.SmartWaiting.domain.member.dto.responseDto.MemberResponseDto;
import OneTwo.SmartWaiting.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // 1. 회원가입
    @PostMapping
    public ResponseEntity<Long> signup(@RequestBody @Valid MemberSignUpRequestDto request) {
        Long memberId = memberService.signup(request);
        return ResponseEntity.created(URI.create("/api/v1/members/" + memberId)).body(memberId);
    }

    // 2. 내 정보 조회
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponseDto> getMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.getMember(memberId));
    }

}
