package OneTwo.SmartWaiting.domain.support.controller;

import OneTwo.SmartWaiting.domain.support.dto.SupportChatRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportChatResponseDto;
import OneTwo.SmartWaiting.domain.support.service.SupportChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09. 고객지원 챗봇(Support Chat) API", description = "고객지원 매뉴얼을 근거로 AI가 자동 응대합니다.")
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;

    @Operation(summary = "AI 챗봇 질문", description = "매뉴얼에 근거해 답변하며, 답할 수 없으면 상담원 연결이 필요함(needsAgent=true)을 반환합니다.")
    @PostMapping("/chat")
    public ResponseEntity<SupportChatResponseDto> chat(@RequestBody @Valid SupportChatRequestDto request) {
        return ResponseEntity.ok(supportChatService.chat(request));
    }
}
