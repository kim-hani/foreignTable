package OneTwo.SmartWaiting.domain.notification.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.notification.redis.SseRedisMessage;
import OneTwo.SmartWaiting.domain.notification.redis.SseRedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // nginx/ALB 기본 idle timeout(60초)보다 짧게 설정
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    // 현재 연결된 사용자들의 SseEmitter를 저장하는 안전한 Map (동시성 방어)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final MemberRepository memberRepository;
    private final SseRedisPublisher sseRedisPublisher;

    // 1. 클라이언트가 구독을 요청할 때 실행되는 메서드 (email = JWT 인증 사용자)
    public SseEmitter subscribe(String email) {
        Long memberId = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .getId();

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

        // 503 에러 방지를 위한 더미(Dummy) 이벤트 최초 발송 — 방금 이 인스턴스에 연결됐으므로 직접 전달
        deliverLocal(memberId, "EventStream Created. [userId=" + memberId + "]");

        return emitter;
    }

    // 2. 특정 사용자에게 알림 데이터를 쏘는 메서드
    // 손님의 연결이 어느 인스턴스에 있는지 모르므로 Redis로 발행 → 연결을 가진 인스턴스가 deliverLocal로 전달
    public void sendToClient(Long memberId, String data) {
        try {
            sseRedisPublisher.publish(SseRedisMessage.send(memberId, data));
        } catch (Exception e) {
            // Redis 장애 시에도 이 인스턴스에 연결된 손님에게는 전달 (단일 인스턴스와 같은 동작 보장)
            log.warn("SSE Redis 발행 실패로 로컬 전달로 대체: 사용자 ID = {}", memberId, e);
            deliverLocal(memberId, data);
        }
    }

    // 이 인스턴스에 연결된 경우에만 전달 (Redis 구독자 SseRedisSubscriber가 호출)
    public void deliverLocal(Long memberId, String data) {
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

    // 연결이 다른 인스턴스에 있을 수 있으므로 종료 요청도 Redis로 발행
    public void closeConnection(Long memberId){
        try {
            sseRedisPublisher.publish(SseRedisMessage.close(memberId));
        } catch (Exception e) {
            log.warn("SSE Redis 발행 실패로 로컬 종료로 대체: 사용자 ID = {}", memberId, e);
            closeLocal(memberId);
        }
    }

    // 이 인스턴스에 연결된 경우에만 종료 (Redis 구독자 SseRedisSubscriber가 호출)
    public void closeLocal(Long memberId){
        SseEmitter emitter = emitters.get(memberId);
        if(emitter != null){
            emitter.complete(); // 클라이언트와의 연결 종료
            emitters.remove(memberId, emitter); // Map에서 제거 (재연결로 교체된 새 연결은 유지)
            log.info("🔒 연결 종료: 사용자 ID = {}", memberId);
        }
    }
}