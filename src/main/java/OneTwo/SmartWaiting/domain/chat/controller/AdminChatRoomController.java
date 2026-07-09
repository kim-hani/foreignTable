package OneTwo.SmartWaiting.domain.chat.controller;

import OneTwo.SmartWaiting.domain.chat.dto.ChatRoomResponseDto;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.chat.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 상담원(ADMIN) 콘솔용 API — /api/v1/admin/** 경로라 SecurityConfig의 hasRole("ADMIN")이 적용된다.
 */
@Tag(name = "11. 상담 채팅(Chat) API")
@RestController
@RequestMapping("/api/v1/admin/chat-rooms")
@RequiredArgsConstructor
public class AdminChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(summary = "[ADMIN] 상담 채팅방 목록",
            description = "상태별 방 목록을 오래된 요청 순(선착순)으로 반환합니다. 기본값은 WAITING(수락 대기).")
    @GetMapping
    public ResponseEntity<Slice<ChatRoomResponseDto>> getRooms(
            Principal principal,
            @RequestParam(defaultValue = "WAITING") ChatRoomStatus status,
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.ok(chatRoomService.getRoomsByStatus(principal.getName(), status, pageable));
    }

    @Operation(summary = "[ADMIN] 상담 수락",
            description = "대기 중(WAITING) 방의 담당 상담원이 됩니다. 이미 다른 상담원이 수락했다면 409를 반환합니다.")
    @PatchMapping("/{roomId}/claim")
    public ResponseEntity<ChatRoomResponseDto> claimRoom(Principal principal, @PathVariable Long roomId) {
        return ResponseEntity.ok(chatRoomService.claimRoom(principal.getName(), roomId));
    }

    @Operation(summary = "[ADMIN] 상담 종료")
    @PatchMapping("/{roomId}/close")
    public ResponseEntity<Void> closeRoom(Principal principal, @PathVariable Long roomId) {
        chatRoomService.closeRoom(principal.getName(), roomId);
        return ResponseEntity.noContent().build();
    }
}
