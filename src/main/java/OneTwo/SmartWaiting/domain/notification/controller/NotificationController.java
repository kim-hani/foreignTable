package OneTwo.SmartWaiting.domain.notification.controller;

import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;

@Tag(name = "07. 알림(Notification) API", description = "SSE 기반 실시간 알림 구독 기능")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 구독 대상은 JWT 인증 사용자로 고정 (경로의 memberId를 믿으면 타인 알림 가로채기 가능)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 알림 구독 (SSE)", description = "로그인한 사용자가 실시간 알림을 받기 위해 연결을 맺습니다. Authorization 헤더가 필요합니다.")
    public SseEmitter subscribe(Principal principal) {
        return notificationService.subscribe(principal.getName());
    }
}
