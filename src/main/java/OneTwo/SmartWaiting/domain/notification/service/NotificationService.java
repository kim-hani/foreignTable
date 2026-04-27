package OneTwo.SmartWaiting.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationService {

    // 현재 연결된 사용자들의 SseEmitter를 저장하는 안전한 Map (동시성 방어)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 1. 클라이언트가 구독을 요청할 때 실행되는 메서드
    public SseEmitter subscribe(Long memberId) {
        // 타임아웃 설정: 60분 (밀리초 단위)
        SseEmitter emitter = new SseEmitter(60 * 1000L * 60);
        emitters.put(memberId, emitter);

        // 클라이언트가 연결을 끊거나 타임아웃이 발생하면 Map에서 삭제
        emitter.onCompletion(() -> emitters.remove(memberId));
        emitter.onTimeout(() -> emitters.remove(memberId));

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
                emitters.remove(memberId); // 전송 실패 시 죽은 연결로 간주하고 삭제
                log.error("❌ 알림 전송 실패: 사용자 ID = {}", memberId, e);
            }
        }
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