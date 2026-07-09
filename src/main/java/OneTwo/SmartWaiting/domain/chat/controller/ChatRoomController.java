package OneTwo.SmartWaiting.domain.chat.controller;

import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.dto.ChatRoomResponseDto;
import OneTwo.SmartWaiting.domain.chat.service.ChatMessageService;
import OneTwo.SmartWaiting.domain.chat.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "11. 상담 채팅(Chat) API",
        description = "AI 챗봇이 답하지 못한 문의를 상담원과 1:1 실시간 채팅으로 이어갑니다. " +
                "메시지 송수신은 WebSocket(STOMP, /ws)으로 이루어집니다.")
@RestController
@RequestMapping("/api/v1/support/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;

    @Operation(summary = "상담 채팅방 생성(상담원 연결 요청)",
            description = "챗봇 응답의 needsAgent=true 시 호출합니다. 이미 열린 방이 있으면 그 방을 그대로 반환합니다(멱등).")
    @PostMapping
    public ResponseEntity<ChatRoomResponseDto> createRoom(Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatRoomService.createRoom(principal.getName()));
    }

    @Operation(summary = "내 상담 채팅방 조회",
            description = "진행 중(WAITING/ACTIVE)인 내 상담 방과 미읽음 개수를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<ChatRoomResponseDto> getMyRoom(Principal principal) {
        return ResponseEntity.ok(chatRoomService.getMyOpenRoom(principal.getName()));
    }

    @Operation(summary = "채팅 메시지 이력 조회",
            description = "방 참여자만 조회할 수 있습니다. 최신 메시지부터 페이지네이션으로 반환합니다.")
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<Slice<ChatMessageResponseDto>> getMessages(
            Principal principal,
            @PathVariable Long roomId,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return ResponseEntity.ok(chatRoomService.getMessages(principal.getName(), roomId, pageable));
    }

    @Operation(summary = "읽음 처리",
            description = "상대방이 보낸 미읽음 메시지를 일괄 읽음 처리합니다. " +
                    "방 진입 시·새 메시지 수신 시 호출하면 상대방 화면에 읽음(READ) 이벤트가 전달됩니다.")
    @PatchMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(Principal principal, @PathVariable Long roomId) {
        chatMessageService.markAsRead(principal.getName(), roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상담 종료",
            description = "방 주인은 대기 취소 겸용으로, 담당 상담원은 상담 종료로 사용합니다.")
    @PatchMapping("/{roomId}/close")
    public ResponseEntity<Void> closeRoom(Principal principal, @PathVariable Long roomId) {
        chatRoomService.closeRoom(principal.getName(), roomId);
        return ResponseEntity.noContent().build();
    }
}
