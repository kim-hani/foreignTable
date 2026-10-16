package OneTwo.SmartWaiting.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationService {

    // nginx/ALB 기본 idle timeout(60초)보다 짧게 설정
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    // 현재 연결된 사용자들의 SseEmitter를 저장하는 안전한 Map (동시성 방어)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 1. 클라이언트가 구독을 요청할 때 실행되는 메서드
    public SseEmitter subscribe(Long memberId) {
        // 타임아웃 설정: 60분 (밀리초 단위)
        SseEmitter emitter = new SseEmitter(60 * 1000L * 60);
        SseEmitter previous = emitters.put(memberId, emitter);

        // 재연결(새로고침 등) 시 이전 연결은 명시적으로 종료
        if (previous != null) {
            previous.complete();
        }

        // 클라이언트가 연결을 끊거나 타임아웃/에러가 발생하면 Map에서 삭제
        // 이전 emitter의 콜백이 새 emitter를 지우지 않도록 (key, value) 일치 시에만 삭제
        emitter.onCompletion(() -> emitters.remove(memberId, emitter));
        emitter.onTimeout(() -> emitters.remove(memberId, emitter));
        emitter.onError(e -> emitters.remove(memberId, emitter));

        // 503 에러 방지를 위한 더미(Dummy) 이벤트 최초 발송
        sendToClient(memberId, "EventStream Created. [userId=" + memberId + "]");

        return emitter;
    }

    // 2. 특정 사용자에게 알림 데이터를 쏘는 메서드
    public void sendToClient(Long memberId, Object data) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter != null) {
            try {
                // "waiting-alert"라는 이벤트 이름으로 데이터를 전송
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(memberId))
                        .name("waiting-alert")
                        .data(data));
                log.info("✅ 알림 전송 성공: 사용자 ID = {}, 내용 = {}", memberId, data);
            } catch (IOException e) {
                emitters.remove(memberId, emitter); // 전송 실패 시 죽은 연결로 간주하고 삭제
                log.error("❌ 알림 전송 실패: 사용자 ID = {}", memberId, e);
            }
        }
    }

    // 3. 프록시/로드밸런서의 idle timeout(통상 60초)에 연결이 끊기지 않도록 주기적으로 comment 이벤트 발송
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        emitters.forEach((memberId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                // 재연결로 교체된 새 emitter는 지우지 않도록 (key, value) 일치 시에만 삭제
                emitters.remove(memberId, emitter);
                log.warn("💔 Heartbeat 실패로 연결 정리: 사용자 ID = {}", memberId);
            }
        });
    }

    public void closeConnection(Long memberId){
        SseEmitter emitter = emitters.get(memberId);
        if(emitter != null){
            emitter.complete(); // 클라이언트와의 연결 종료
            emitters.remove(memberId); // Map에서 제거
            log.info("🔒 연결 종료: 사용자 ID = {}", memberId);
        }
    }
}