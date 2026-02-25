package OneTwo.SmartWaiting.domain.waiting.controller;

import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    // 대기 등록
    @PostMapping
    public ResponseEntity<Long> registerWaiting(
            Principal principal,
            @RequestBody @Valid WaitingRegisterRequestDto request) {
        Long waitingId = waitingService.registerWaiting(request,principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/waitings/" + waitingId)).body(waitingId);
    }

    // 대기 취소
    @PatchMapping("/{waitingId}/cancel")
    public ResponseEntity<Void> cancelWaiting(
            @PathVariable Long waitingId,
            Principal principal) {
        waitingService.cancelWaiting(waitingId,principal.getName());
        return ResponseEntity.ok().build();
    }

    // 내 웨이팅 목록 조회
    @GetMapping("/my")
    public ResponseEntity<List<WaitingResponse>> getMyWaitings(Principal principal) {
        return ResponseEntity.ok(waitingService.getMyWaitings(principal.getName()));
    }

    // 가게 웨이팅 목록 조회
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<WaitingResponse>> getStoreWaitings(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "WAITING") WaitingStatus status,
            Principal principal
    ) {
        return ResponseEntity.ok(waitingService.getStoreWaitings(storeId, status, principal.getName()));
    }

    // 웨이팅 상태 변경
    @PatchMapping("/{waitingId}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long waitingId,
            Principal principal,
            @RequestBody @Valid WaitingChangeRequestDto request) {
        waitingService.changeStatus(waitingId, request, principal.getName());
        return ResponseEntity.ok().build();
    }
}
