# 04. 알림 기능 (SSE + FCM)

> 웹 실시간 알림(SSE)과 모바일 푸시(FCM)의 이중 채널 전략, 순번 변동 알림, 리뷰 요청 자동 푸시

---

## 1. 알림 이중 채널 전략

SmartWaiting은 **상황에 맞는 두 가지 알림 채널**을 사용합니다.

| 채널 | 기술 | 도달 조건 | 사용 시나리오 |
|---|---|---|---|
| **SSE** | `SseEmitter` | 웹 페이지가 열려 연결 중 | 실시간 순번 갱신, 호출 알림 |
| **FCM** | Firebase Admin | 앱 미실행/백그라운드여도 도달 | 입장 직전 푸시, 리뷰 요청 푸시 |

```
                 ┌─────────────── 웹 브라우저 (열려있음)
   서버 이벤트 ──┤  SSE: 실시간 단방향 스트림
                 └─────────────── 모바일 앱 (꺼져있어도)
                    FCM: 운영체제 푸시
```

**왜 둘 다 쓰는가?**
- SSE는 가볍고 실시간이지만 **페이지를 닫으면 못 받음**
- FCM은 앱이 꺼져 있어도 도달하지만 **외부 인프라 의존**
- → "입장 직전" 같은 중요 알림은 **SSE + FCM 동시 발송**으로 도달률 극대화

---

## 2. SSE 실시간 알림 — NotificationService

### 2.1 구독 메커니즘

```java
@Service
public class NotificationService {
    // 회원ID → 연결(emitter) 매핑 (동시성 안전)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(60 * 1000L * 60);  // 60분 타임아웃
        emitters.put(memberId, emitter);

        emitter.onCompletion(() -> emitters.remove(memberId));  // 정상 종료 시 정리
        emitter.onTimeout(() -> emitters.remove(memberId));      // 타임아웃 시 정리

        // 503 방지용 더미 이벤트 즉시 발송 (연결 확립)
        sendToClient(memberId, "EventStream Created. [userId=" + memberId + "]");
        return emitter;
    }
}
```

### 2.2 구독 흐름

```
클라이언트
  │  GET /api/v1/notifications/subscribe/{memberId}
  │  (Accept: text/event-stream)
  ▼
NotificationController.subscribe()
  ▼
NotificationService.subscribe(memberId)
  ├─ SseEmitter 생성 (60분 타임아웃)
  ├─ emitters 맵에 등록
  ├─ 연결 종료/타임아웃 시 자동 제거 콜백 등록
  └─ 더미 이벤트 발송 (연결 확립 확인)
  ▼
이후 서버 이벤트 발생 시 emitter.send()로 실시간 push
```

### 2.3 알림 발송 & 연결 종료

```java
public void sendToClient(Long memberId, Object data) {
    SseEmitter emitter = emitters.get(memberId);
    if (emitter != null) {
        try {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(memberId))
                .name("waiting-alert")   // 모든 알림 이벤트명 통일
                .data(data));
        } catch (IOException e) {
            emitters.remove(memberId);   // 전송 실패 = 죽은 연결로 간주, 제거
        }
    }
}

public void closeConnection(Long memberId) {  // 종료 상태(착석/취소/노쇼) 시
    SseEmitter emitter = emitters.get(memberId);
    if (emitter != null) {
        emitter.complete();      // 연결 정상 종료
        emitters.remove(memberId);
    }
}
```

**메모리 누수 방지 설계**:
- 전송 실패(`IOException`) → 즉시 맵에서 제거
- 종료 상태 도달 → `closeConnection()`으로 명시적 정리
- 타임아웃/완료 콜백 → 자동 제거

> ⚠️ **현재 한계**: `ConcurrentHashMap`은 **단일 인스턴스 메모리**에만 존재. 서버를 여러 대로 확장하면 다른 인스턴스에 연결된 사용자에게 알림이 안 감. → Redis Pub/Sub 기반 확장이 향후 과제.

---

## 3. FCM 모바일 푸시 — FcmService

### 3.1 초기화 — FirebaseConfig

```java
@Configuration
public class FirebaseConfig {
    @PostConstruct
    public void init() {
        // classpath의 서비스 계정 JSON으로 Firebase 앱 초기화
        InputStream serviceAccount = getClass()
            .getResourceAsStream("/firebase-service-account.json");
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
```
> 서비스 계정 JSON은 **절대 커밋 금지**(`.gitignore` 등록). 운영 환경에서는 별도 주입.

### 3.2 두 가지 푸시 메시지

```java
@Service
public class FcmService {
    // ① 입장 직전 알림 (웨이팅 1번째)
    public void sendFirstInLinePush(String targetToken, Long storeId) {
        if (targetToken == null || targetToken.isEmpty()) return;  // 토큰 없으면 skip
        Message message = Message.builder()
            .setToken(targetToken)
            .setNotification(Notification.builder()
                .setTitle("입장 직전 알림")
                .setBody("🚨 곧 입장하실 차례입니다! 매장 앞에 대기해 주세요.")
                .build())
            .putData("type", "FIRST_IN_LINE")        // 앱이 분기 처리할 타입
            .putData("storeId", String.valueOf(storeId))  // 클릭 시 이동할 가게
            .build();
        FirebaseMessaging.getInstance().send(message);
    }

    // ② 리뷰 요청 알림 (착석 1시간 후)
    public void sendReviewRequestPush(String targetToken, String title, String body, Long storeId) {
        ...
        .putData("type", "REVIEW_REQUEST")
        .putData("storeId", String.valueOf(storeId))
        ...
    }
}
```

**데이터 페이로드 설계**: `type`과 `storeId`를 담아 보내, 앱이 알림 클릭 시 적절한 화면(가게 페이지/리뷰 작성)으로 **딥링크 이동**할 수 있게 함.

---

## 4. 순번 변동 알림 로직

웨이팅 상태가 바뀔 때(취소/착석/노쇼/미루기) 대기열 순서가 변하면, 해당하는 손님에게 알림을 보냅니다.

### 4.1 알림 트리거 지점

| 트리거 | 발생 위치 | 알림 |
|---|---|---|
| 등록 시 내가 3번째 | `registerWaiting` | SSE: "3번째입니다" |
| 사장님이 호출(CALL) | `changeStatus` | SSE: "사장님이 호출하셨습니다" |
| 상위 손님 이탈(착석/취소/노쇼) | `changeStatus`, `cancelWaiting`, `postpone` | 새 1번째 → SSE+FCM, 새 3번째 → SSE |

### 4.2 "1번째 손님" 알림 — checkAndNotifyFirstInLine

상위 손님이 빠져 **새로운 1등이 생겼을 때** SSE + FCM 동시 발송.

```java
private void checkAndNotifyFirstInLine(Long storeId) {
    // ticketTime 오름차순 정렬 후 0번째(=현재 1등) 1명 조회
    PageRequest pageRequest = PageRequest.of(0, 1);
    Page<Waiting> firstPage = waitingRepository
        .findByStoreIdAndStatusOrderByTicketTimeAsc(storeId, WAITING, pageRequest);

    if (firstPage.hasContent()) {
        Waiting first = firstPage.getContent().get(0);
        Long memberId = first.getMember().getId();
        String fcmToken = first.getMember().getFcmToken();

        notificationService.sendToClient(memberId,           // SSE
            "🚨 곧 입장하실 차례입니다! 매장 앞에 대기해 주세요.");
        fcmService.sendFirstInLinePush(fcmToken, storeId);   // FCM
    }
}
```

### 4.3 "3번째 손님" 알림 — checkAndNotifyThirdInLine

새로운 3등에게 "매장으로 이동하라"는 SSE 알림.

```java
private void checkAndNotifyThirdInLine(Long storeId) {
    PageRequest pageRequest = PageRequest.of(2, 1);  // 0-indexed → 2 = 3번째
    Page<Waiting> thirdPage = waitingRepository
        .findByStoreIdAndStatusOrderByTicketTimeAsc(storeId, WAITING, pageRequest);

    if (thirdPage.hasContent()) {
        Waiting third = thirdPage.getContent().get(0);
        notificationService.sendToClient(third.getMember().getId(),
            "📢 대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요.");
    }
}
```

### 4.4 중복 알림 방지 설계

```
상태 변경 전에 isTop3 / isTop2 판정 (변경 후엔 이미 빠져서 판정 불가)
  │
  ▼
실제 상태 변경 + flush()  ★ DB 반영 보장
  │
  ▼
shouldNotify(isTop3)면 → 새 3번째 손님 조회 후 알림
shouldNotifyFirst(isTop2)면 → 새 1번째 손님 조회 후 알림
```

**핵심**: "내가 상위 1~3등에 있었다가 빠진 경우에만" 알림. 뒤쪽 손님이 빠지면 순서 변동이 없으므로 알림하지 않음 → **불필요한 알림 최소화**. (상세 판정 로직은 [03-waiting](./03-waiting.md) §8)

> ⚠️ `flush()` 순서 보장은 알림 정확성의 핵심. 상태 변경이 DB에 반영된 후 조회해야 올바른 대상이 잡힘.

---

## 5. 리뷰 요청 자동 푸시 — ReviewNotificationScheduler

착석(SEATED) 후 **정확히 1시간 뒤** 자동으로 리뷰 작성을 유도하는 푸시.

```java
@Scheduled(cron = "0 * * * * *")  // 매분 0초 실행
@Transactional(readOnly = true)
public void sendReviewRequestNotifications() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime start = now.minusMinutes(61);
    LocalDateTime end   = now.minusMinutes(60);

    // 60~61분 전에 착석(SEATED) 상태가 된 사람들 조회
    List<Waiting> targets = waitingRepository
        .findAllByStatusAndUpdatedAtBetween(SEATED, start, end);

    for (Waiting w : targets) {
        Member member = w.getMember();
        fcmService.sendReviewRequestPush(
            member.getFcmToken(),
            "식사는 맛있게 하셨나요? ",
            "방문하셨던 '" + w.getStore().getName() + "' 의 소중한 리뷰를 남겨주세요! ⭐️",
            w.getStore().getId()
        );
    }
}
```

### 동작 원리

```
매분 실행
  │
  ▼
"60분 전 ~ 61분 전" 사이에 SEATED로 바뀐 웨이팅 조회
  │   (updatedAt = 착석 시각, BaseEntity가 자동 기록)
  ▼
각 손님에게 FCM 리뷰 요청 푸시 (가게 이름·ID 포함)
```

**왜 1분 윈도우(60~61분)인가?**
매분 실행되며 "딱 1분치 구간"만 조회 → 같은 사람에게 **중복 발송 방지**. `updatedAt`이 정확히 1시간~1시간1분 전인 사람만 한 번 잡힘.

**왜 1시간 후인가?**
식사를 마칠 즈음에 리뷰를 요청해야 응답률이 높음. 너무 이르면 식사 중, 너무 늦으면 잊어버림.

---

## 6. 알림 시나리오 종합 예시

**상황**: A(1등), B(2등), C(3등), D(4등)가 대기 중. 사장님이 A를 착석(SEATED) 처리.

```
1. 사장님: PATCH /waitings/{A}/status {SEATED}
2. 변경 전 판정: A는 isTop2(1등)=true, isTop3=true
3. A.status = SEATED, flush()
4. checkAndNotifyThirdInLine → 새 3번째 = D → D에게 SSE "3번째입니다"
5. checkAndNotifyFirstInLine → 새 1번째 = B → B에게 SSE+FCM "곧 입장입니다"
6. A의 SSE 연결 종료(closeConnection)
7. (1시간 후) ReviewNotificationScheduler → A에게 FCM "리뷰 남겨주세요"
```

결과: B는 입장 임박 알림(SSE+FCM), C는 변동 없음, D는 3번째 알림(SSE), A는 1시간 후 리뷰 요청.

---

## 7. 알림 API 요약

| 엔드포인트 | 메서드 | 설명 |
|---|---|---|
| `/api/v1/notifications/subscribe/{memberId}` | GET (SSE) | 실시간 알림 구독 연결 |

> 나머지 알림은 웨이팅 상태 변경·스케줄러에서 **자동 발송**되며 별도 API가 없음.
