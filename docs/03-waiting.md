# 03. 웨이팅 (핵심 도메인)

> 분산락 기반 동시성 제어, 상태 머신, 순번·예상시간 실시간 계산, 미루기, 블랙리스트, 유령 웨이팅 정리

이 도메인이 SmartWaiting의 심장입니다. **여러 손님이 동시에 등록해도 순번이 꼬이지 않는 것**이 가장 중요한 요구사항입니다.

---

## 1. 웨이팅 상태 머신

```java
public enum WaitingStatus {
    WAITING,  // 대기 중
    CALL,     // 사장님이 호출함 (입장 직전)
    SEATED,   // 착석 완료 (정상 종료)
    CANCEL,   // 취소 (손님/시스템)
    NOSHOW    // 노쇼 (호출했으나 미입장)
}
```

```
                ┌──────────────────────────────────┐
                │                                    │
   [등록] ──▶ WAITING ──(사장님 호출)──▶ CALL ──(입장)──▶ SEATED ✅
                │  │                       │
                │  │                       └──(미입장)──▶ NOSHOW ⚠️ (노쇼 카운트++)
                │  │
                │  └──(손님 취소)──────────────────────▶ CANCEL
                │
                └──(미루기 ×최대2회: ticketTime 갱신, 상태 유지)
```

| 종료 상태 | 의미 | 부가 효과 |
|---|---|---|
| `SEATED` | 정상 입장 | 1시간 후 리뷰 요청 푸시 대상 |
| `CANCEL` | 취소됨 | SSE 연결 종료 |
| `NOSHOW` | 노쇼 | `noShowCount++` → 3회 시 블랙리스트 |

---

## 2. Waiting 엔티티

```java
public class Waiting extends BaseEntity {
    @ManyToOne(LAZY) private Store store;
    @ManyToOne(LAZY) private Member member;

    private Integer headCount;        // 인원 수
    private WaitingStatus status;
    private Integer queueNumber;      // 등록 시점의 순번 스냅샷(불변)
    private Integer expectedWaitMin;  // 예상 대기 시간(분)
    private Integer postponedCount;   // 미루기 횟수 (최대 2)
    private LocalDateTime ticketTime; // ★ 순번 계산의 기준 시각

    public void changeStatus(WaitingStatus status) { this.status = status; }

    public void postpone(){
        if(this.postponedCount >= 2)
            throw new IllegalStateException("더 이상 대기 순서를 미룰 수 없습니다.");
        this.postponedCount++;
        this.ticketTime = LocalDateTime.now();  // 티켓 시각을 현재로 → 맨 뒤로 이동
    }
}
```

### 핵심 개념: `ticketTime`
순번은 `queueNumber`(등록 시점 스냅샷)가 아니라 **`ticketTime`(발권 시각)으로 실시간 계산**됩니다.
- "내 앞의 팀 수" = 나보다 `ticketTime`이 빠른 WAITING/CALL 상태의 팀 수
- 미루기를 하면 `ticketTime`이 현재 시각으로 갱신 → 자연스럽게 맨 뒤로 이동
- 앞 사람이 취소/착석하면 별도 갱신 없이도 내 순번이 자동으로 당겨짐

---

## 3. ⭐ 웨이팅 등록 — 분산락으로 동시성 제어

### 3.1 문제: Race Condition

여러 손님이 **동시에** 같은 식당에 등록하면?
- 손님 A, B가 거의 동시에 "현재 5명 대기 중" 조회
- 둘 다 "나는 6번"이라고 계산 → **순번 중복** 발생
- 최대 대기 수 제한(만석 체크)도 동시 요청에 뚫림

### 3.2 해결: Redisson 분산락 (Facade 계층)

`WaitingLockFacade`가 Service를 감싸 **가게 단위 락**을 겁니다.

```java
@Component
public class WaitingLockFacade {
    private final RedissonClient redissonClient;
    private final WaitingService waitingService;

    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {
        String lockKey = "waiting:store:" + requestDto.storeId();  // 가게별 락
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean isLocked = lock.tryLock(10, 5, TimeUnit.SECONDS);
            // 최대 10초 대기, 획득 시 5초간 보유
            if (!isLocked) {
                throw new RuntimeException("현재 대기 요청이 많아 처리가 지연...");
            }
            return waitingService.registerWaiting(requestDto, email);  // 락 안에서 실행
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("시스템 오류가 발생했습니다.");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();  // 반드시 해제
            }
        }
    }
}
```

**왜 가게(store) 단위 락인가?**
- 락 키 = `waiting:store:{storeId}` → 같은 가게 등록만 직렬화
- 다른 가게 등록은 서로 영향 없이 병렬 처리 → 성능 유지하며 정확성 확보

**왜 Redisson인가?**
- 단일 서버 `synchronized`는 다중 인스턴스 환경에서 무력 → Redis 기반 **분산**락 필요
- `tryLock(대기시간, 보유시간)`으로 데드락·무한대기 방지 (보유시간 후 자동 해제)

### 3.2.1 분산락이 실제로 어떻게 막는가 (작동 원리)

**① Race Condition이 발생하는 순간**
락이 없다면 손님 A·B의 요청이 겹칠 때 다음처럼 꼬입니다.

```
시각  손님 A                    손님 B
 t1   "현재 5팀" 조회
 t2                            "현재 5팀" 조회   ← A가 아직 저장 전이라 똑같이 5팀으로 읽음
 t3   6번으로 저장
 t4                            6번으로 저장      ← 순번 6 중복! (만석 체크도 동일하게 뚫림)
```

문제의 본질은 "**조회 → 계산 → 저장**"이 원자적이지 않아, 그 사이에 다른 요청이 끼어드는 것입니다.

**② Redis 단일 키 점유로 직렬화**
Redisson의 `getLock(key)`은 Redis에 그 키를 **단 하나의 클라이언트만** 소유할 수 있는 항목으로 만듭니다. A가 `waiting:store:1` 락을 잡으면, B의 `tryLock`은 그 키가 비워질 때까지 대기합니다. 결국 A의 "조회→계산→저장→커밋"이 전부 끝나고 락이 풀린 뒤에야 B가 진입해, B는 **갱신된 6팀**을 읽고 7번으로 저장합니다. 동시 요청이 한 줄로 세워지는 것입니다.

**③ `tryLock(10, 5, SECONDS)`의 두 숫자**

| 인자 | 값 | 의미 |
|---|---|---|
| waitTime | **10초** | 락을 얻기 위해 최대 10초 대기. 그 안에 못 얻으면 `false` 반환 → "요청이 많아 지연" 안내로 **빠르게 실패**(무한 대기 방지) |
| leaseTime | **5초** | 락을 잡은 뒤 최대 5초간 보유. 5초가 지나면 **무조건 자동 해제** |

> ⚠️ **watchdog이 동작하지 않는다는 점에 주의**: Redisson은 leaseTime을 생략하면 watchdog이 락을 자동 연장해 주지만, 여기서는 **leaseTime(5초)을 명시**했기 때문에 watchdog이 비활성화됩니다. 즉 "락을 쥔 스레드가 5초 안에 끝낸다"는 가정이 전제입니다. 만약 작업이 5초를 넘기면 락이 먼저 풀려 동시성 보호가 깨질 수 있으므로, 등록 로직은 5초 내에 끝나야 안전합니다. leaseTime을 둔 이유는 **장애로 스레드가 죽어 unlock을 못 해도 5초 뒤 강제 해제되어 데드락을 막기 위함**입니다.

**④ 왜 가게(store) 단위 키인가**
락 키가 `waiting:store:{storeId}`라서 **같은 가게**의 등록만 직렬화되고, 다른 가게의 등록은 서로 다른 키를 잡으므로 **병렬로** 처리됩니다. 전역 락(모든 등록을 하나의 락으로)으로 묶었다면 정확하긴 해도 식당이 많아질수록 병목이 됩니다. 가게 단위로 쪼개 **정확성과 처리량을 동시에** 얻습니다.

**⑤ 해제 안전장치와 한계**
`finally`에서 `lock.isLocked() && lock.isHeldByCurrentThread()`를 확인하고 해제합니다 — 내가 잡은 락만 풀어 "남의 락을 푸는" 사고를 방지합니다. 다만 현재 `RedissonConfig`는 `useSingleServer()`(단일 Redis 노드)라 **이 노드가 죽으면 분산락도 멈춥니다**. 운영에서는 Redis 이중화가 필요하며([08-aws-infrastructure](./08-aws-infrastructure.md) §5.3), SSE까지 포함한 수평 확장 과제는 [04-notification](./04-notification.md) §2.3을 참고하세요.

### 3.3 등록 처리 파이프라인 (락 내부)

```
WaitingService.registerWaiting()
  │
  ① 식당 존재 확인 ────────────────▶ 없으면 STORE_NOT_FOUND
  │
  ② 회원 존재 확인
  │
  ③ 웨이팅 접수 중인가? ───────────▶ 중단 상태면 STORE_NOT_ACCEPTING_WAITING
  │  (store.isAcceptingWaiting)
  │
  ④ 만석인가? (maxWaitingCount 설정 시) ─▶ 초과면 WAITING_QUEUE_FULL
  │  현재 WAITING+CALL 수 ≥ 최대치
  │
  ⑤ 블랙리스트인가? ───────────────▶ 노쇼 3회+ 면 BLACKLISTED_MEMBER
  │  (member.isBlacklisted)
  │
  ⑥ 이미 대기 중인가? ─────────────▶ 중복이면 WAITING_ALREADY_EXISTS
  │
  ⑦ 내 순번 계산
  │  myQueueNumber = (현재 WAITING+CALL 수) + 1
  │  expectedWaitMin = myQueueNumber × store.averageWaiting
  │
  ⑧ Waiting 저장 (status=WAITING, ticketTime=now)
  │
  ⑨ 내가 3번째면? ──▶ SSE 알림 "대기 순번이 3번째입니다!"
  │
  ▼
waitingId 반환
```

**흐름 설명** — 검증은 "값싼 것 → 비싼 것, 일반 차단 → 개인 차단" 순서로 쌓여 있다 (8단계 모두 **락 안에서** 실행되어 동시 요청에 흔들리지 않음):

- **① ② 전제 조건**: 식당·회원이 존재하는지 먼저 확인.
- **③ ④ 가게 전체 조건**: 접수 중단·만석 여부 확인.
- **⑤ ⑥ 손님 개인 조건**: 블랙리스트·중복 등록 여부 확인.
- **⑦ 순번 계산**: 모든 관문 통과 후 "현재 WAITING+CALL 수 + 1" → **락이 보장하는 최신 값** 위에서 계산.
- **⑧ ⑨ 저장·알림**: 저장 후 내가 마침 3번째면 그 손님에게만 SSE 안내 발송.

---

## 4. 순번·예상 대기시간 실시간 계산

### 4.1 계산 공식

핵심 쿼리: **나보다 `ticketTime`이 빠른 WAITING·CALL 팀 수 세기**
```java
long teamsAhead = waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(
    storeId,
    Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL),
    waiting.getTicketTime()
);

int currentRank   = teamsAhead + 1;                       // 내 순번
int expectedWaitMin = teamsAhead × store.getAverageWaiting(); // 예상 대기(분)
```

> `averageWaiting`은 가게가 설정한 팀당 평균 대기시간(기본 10분). 앞 팀이 3팀이면 30분 예상.

### 4.2 실시간 현황 단건 조회 — `GET /api/v1/waitings/{id}/status`

손님이 폴링으로 자신의 현재 순번을 확인하는 API.

```java
public WaitingStatusResponse getWaitingStatus(Long waitingId, String email) {
    // 본인 확인
    if (!waiting.getMember().getId().equals(member.getId()))
        throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);

    // WAITING/CALL일 때만 실시간 계산, 종료 상태면 0
    if (status == WAITING || status == CALL) {
        teamsAhead = count(...);
        expectedWaitMin = teamsAhead × averageWaiting;
        currentRank = teamsAhead + 1;
    } else {
        teamsAhead = 0; expectedWaitMin = 0; currentRank = 0;
    }
    return new WaitingStatusResponse(status, currentRank, teamsAhead, expectedWaitMin);
}
```

| 응답 필드 | 의미 |
|---|---|
| `status` | 현재 상태 |
| `currentRank` | 내 순번 (1 = 다음 차례) |
| `teamsAhead` | 내 앞의 팀 수 |
| `expectedWaitMin` | 예상 대기 시간(분) |

---

## 5. 상태 변경 (사장님) — changeStatus

`PATCH /api/v1/waitings/{id}/status` — **OWNER 권한 필수**

```
사장님이 상태 변경 요청 (CALL/SEATED/CANCEL/NOSHOW)
  │
  ▼ 점주 본인 가게인지 검증 (UNAUTHORIZED_STORE_OWNER)
  │
  ▼ 변경 전 알림 대상 여부 미리 판정 (isTop3, isTop2)
  │
  ▼ waiting.changeStatus(newStatus)
  │
  ▼ NOSHOW면 → member.incrementNoShowCount()  (블랙리스트 누적)
  │
  ▼ waitingRepository.flush()  ★ DB 반영 강제 (알림 전 필수)
  │
  ▼ 상태별 후속 알림 분기:
     ├─ CALL  → 해당 손님에게 SSE "사장님이 호출하셨습니다!"
     └─ SEATED/CANCEL/NOSHOW (이탈) →
          ├─ 앞자리 변동 발생 → 새 3번째 손님에게 SSE 알림
          ├─ 1등 이탈 → 새 1번째 손님에게 SSE + FCM 알림
          └─ 해당 손님 SSE 연결 종료
```

**흐름 설명** — 가장 까다로운 점은 **알림 대상을 변경 "전"에 미리 판정**한다는 것이다(`isTop3`/`isTop2`). 상태를 바꾸고 나면 그 손님은 이미 대기열에서 빠져 "원래 상위권이었는지"를 되짚을 수 없기 때문:

- **① 사전 판정**: 변경 전에 변동 영향 여부(상위권 이탈인지)를 미리 기억해 둔다.
- **② 상태 변경 + flush()**: 상태를 바꾼 뒤 `flush()`로 DB에 확정한다.
- **③ 새 대상 조회·알림**: 그 시점의 대기열을 다시 조회해 새 1·3번째 손님에게 알린다.
- **알림 분기**: CALL(호출)은 순서 변동이 아니라 그 손님 **본인**에게, SEATED/CANCEL/NOSHOW는 상위권 이탈이라 **뒷사람**(새 1·3번째)에게 보낸다.

> 자세한 판정 로직은 §8 참고.

### ⚠️ `flush()`가 필수인 이유
알림은 "현재 대기열 순서"를 다시 조회해 대상을 찾습니다. 상태 변경이 **DB에 반영되기 전**에 알림 로직이 돌면, 방금 이탈한 사람이 여전히 대기열에 있는 것으로 잡혀 **잘못된 대상에게 알림**이 갑니다. 따라서 `flush()`로 변경을 먼저 DB에 밀어넣은 뒤 알림을 보냅니다.

> 자세한 알림 로직은 [04-notification](./04-notification.md) 참고.

---

## 6. 웨이팅 취소 & 미루기 (손님)

### 6.1 취소 — `PATCH /api/v1/waitings/{id}/cancel`
```
본인 확인 → 알림대상 사전판정 → status=CANCEL → flush()
  → (이탈로 인한) 3번째·1번째 손님 알림 → 본인 SSE 연결 종료
```

### 6.2 미루기 — `PATCH /api/v1/waitings/{id}/postpone`
```
본인 확인
  → WAITING 상태인가? (아니면 INVALID_WAITING_STATUS)
  → 알림대상 사전판정
  → waiting.postpone()  ── postponedCount++, ticketTime=now (맨 뒤로)
       └─ 이미 2회 미뤘으면 IllegalStateException
  → flush()
  → 내가 빠진 자리의 3번째·1번째 손님에게 알림
```

> **미루기 제한 2회**: 무한 미루기로 다른 손님이 피해보는 것을 방지.

---

## 7. 블랙리스트 (노쇼 패널티)

```
노쇼 발생 (사장님이 NOSHOW로 변경)
  → member.incrementNoShowCount()
  → noShowCount 가 3 이상이 되면 isBlacklisted() = true
  → 이후 웨이팅 등록 시 ⑤단계에서 BLACKLISTED_MEMBER로 차단
```

```java
public boolean isBlacklisted() {
    return this.noShowCount >= 3;  // 노쇼 3회 누적 = 영구 차단
}
```

> 상습 노쇼 손님으로부터 식당을 보호하는 장치.

---

## 8. 순번 판정 헬퍼 — isTop2 / isTop3

알림을 "정확히 순서가 바뀐 경우에만" 보내기 위한 판정 메서드.

```java
boolean isTop3(Waiting waiting) {   // 내가 1~3등인가? (3번째 알림 대상 변동 판단)
    if (종료상태) return false;
    long teamsAhead = count(나보다 빠른 WAITING/CALL);
    return teamsAhead < 3;          // 앞에 0~2팀 = 내가 1·2·3등
}

boolean isTop2(Waiting waiting) {   // 내가 정확히 1등인가? (1번째 알림 대상 변동 판단)
    if (종료상태) return false;
    long teamsAhead = count(나보다 빠른 WAITING/CALL);
    return teamsAhead == 0;         // 내가 빠지면 새로운 1등 발생
}
```

**왜 변경 "전"에 판정하는가?**
내가 대기열 상위(1~3등)에 있다가 빠질 때만 "뒷사람의 순번이 올라가는" 의미 있는 변동이 생깁니다. 이때만 알림을 보내 **불필요한 알림을 차단**합니다. (예: 50번째 손님이 취소해도 아무도 알림받지 않음)

---

## 9. 유령 웨이팅 정리 — CleanupScheduler

영업 종료 후에도 `WAITING`/`CALL`로 남은 "유령 웨이팅"을 매일 정리하는 배치.

```java
@Scheduled(cron = "0 0 4 * * ?")  // 매일 새벽 4시
@Transactional
public void cleanupGhostWaitings(){
    // 1단계: 미종료 웨이팅 전부 CANCEL 처리 + 안내 알림 + 연결 종료
    List<Waiting> ghosts = waitingRepository.findAllByStatusIn(WAITING, CALL);
    for (Waiting w : ghosts) {
        w.changeStatus(CANCEL);
        notificationService.closeConnection(w.getMember().getId());
        notificationService.sendToClient(w.getMember().getId(),
            "영업 시간이 종료되어 대기가 자동 취소되었습니다...");
    }

    // 2단계: 어제 웨이팅이 있었던 가게들의 통계 집계 (06 문서 참고)
    List<Long> activeStoreIds = waitingRepository.findStoreIdsWithWaitingsSince(yesterday);
    for (Long storeId : activeStoreIds) {
        try { storeStatisticService.calculateAndSaveStoreStats(storeId); }
        catch (Exception e) { log.error(...); }  // 가게별 try-catch로 전체 실패 방지
    }
}
```

| 단계 | 작업 | 목적 |
|---|---|---|
| 1 | 유령 웨이팅 일괄 취소 | 다음 날 깨끗한 대기열로 시작 |
| 2 | 가게별 혼잡도 통계 집계 | 요일·시간별 통계 갱신 ([06](./06-store-statistics.md) §4) |

> 가게별 통계는 try-catch로 감싸 한 가게 실패가 전체 배치를 중단시키지 않도록 격리.

---

## 10. 웨이팅 API 요약

| 엔드포인트 | 메서드 | 권한 | 설명 |
|---|---|---|---|
| `/api/v1/waitings` | POST | USER | 웨이팅 등록 (분산락) |
| `/api/v1/waitings/{id}/cancel` | PATCH | 본인 | 취소 |
| `/api/v1/waitings/{id}/postpone` | PATCH | 본인 | 미루기 (최대 2회) |
| `/api/v1/waitings/{id}/status` | GET | 본인 | 실시간 현황 조회 |
| `/api/v1/waitings/my` | GET | USER | 내 웨이팅 목록 |
| `/api/v1/waitings/store/{storeId}` | GET | OWNER | 가게 대기열 조회(상태별) |
| `/api/v1/waitings/{id}/status` | PATCH | OWNER | 상태 변경(호출/착석/노쇼) |
