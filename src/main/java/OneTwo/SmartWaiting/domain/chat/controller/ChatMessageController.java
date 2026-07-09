package OneTwo.SmartWaiting.domain.chat.controller;

import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageSendRequestDto;
import OneTwo.SmartWaiting.domain.chat.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket 메시지 수신 컨트롤러 — 클라이언트는 "/pub/chat/room/{roomId}"로 SEND 한다.
 * Principal은 StompAuthChannelInterceptor가 CONNECT 시 심은 인증 정보이며,
 * getName()이 email인 것은 기존 REST 컨트롤러와 동일하다.
 */
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat/room/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload @Valid ChatMessageSendRequestDto request,
            Principal principal
    ) {
        chatMessageService.sendMessage(principal.getName(), roomId, request);
    }
}
