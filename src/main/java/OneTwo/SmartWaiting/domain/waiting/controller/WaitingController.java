package OneTwo.SmartWaiting.domain.waiting.controller;

import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.service.WaitingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(name = "4. 웨이팅(Waiting) API", description = "웨이팅 등록, 취소, 조회 및 상태 변경 기능")
@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    // 대기 등록
    @Operation(summary = "웨이팅 등록", description = "손님이 특정 식당에 웨이팅을 등록합니다.")
    @PostMapping
    public ResponseEntity<Long> registerWaiting(
            Principal principal,
            @RequestBody @Valid WaitingRegisterRequestDto request) {
        Long waitingId = waitingService.registerWaiting(request,principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/waitings/" + waitingId)).body(waitingId);
    }

    // 대기 취소
    @Operation(summary = "웨이팅 취소", description = "손님이 본인의 웨이팅을 취소합니다.")
    @PatchMapping("/{waitingId}/cancel")
    public ResponseEntity<Void> cancelWaiting(
            @PathVariable Long waitingId,
            Principal principal) {
        waitingService.cancelWaiting(waitingId,principal.getName());
        return ResponseEntity.ok().build();
    }

    // 내 웨이팅 목록 조회
    @Operation(summary = "내 웨이팅 목록 조회", description = "손님이 본인의 전체 웨이팅 내역을 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<List<WaitingResponse>> getMyWaitings(Principal principal) {
        return ResponseEntity.ok(waitingService.getMyWaitings(principal.getName()));
    }

    // 가게 웨이팅 목록 조회
    @Operation(summary = "가게 웨이팅 목록 조회", description = "사장님이 본인 식당의 현재 대기열을 상태별로 조회합니다.")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<WaitingResponse>> getStoreWaitings(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "WAITING") WaitingStatus status,
            Principal principal
    ) {
        return ResponseEntity.ok(waitingService.getStoreWaitings(storeId, status, principal.getName()));
    }

    // 웨이팅 상태 변경
    @Operation(summary = "웨이팅 상태 변경", description = "사장님이 특정 손님의 웨이팅 상태(호출, 입장, 노쇼 등)를 변경합니다.")
    @PatchMapping("/{waitingId}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long waitingId,
            Principal principal,
            @RequestBody @Valid WaitingChangeRequestDto request) {
        waitingService.changeStatus(waitingId, request, principal.getName());
        return ResponseEntity.ok().build();
    }
}
