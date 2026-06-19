# SmartWaiting AI Agent Development Guidelines

## 1. Project Overview

- **목적**: 음식점 대기 줄 관리 플랫폼 (웨이팅 등록, 실시간 알림, AI 리뷰 요약)
- **기술 스택**: Spring Boot 4.0.1 / Java 17 / PostgreSQL+PostGIS / Redis+Redisson / Spring Security+JWT / OAuth2(Google) / Spring AI(Gemini 2.5 Flash) / Firebase FCM / SSE
- **빌드**: Gradle, JaCoCo 커버리지 포함
- **환경**: Docker Compose (`docker-compose.yml`) — db(PostGIS), redis, app 3개 서비스

---

## 2. Package Architecture

```
OneTwo.SmartWaiting
├── auth/                   # 인증 (controller, service, dto, entity, repository)
├── config/                 # Spring 설정 빈 (Security, JWT, Redis, Firebase, Swagger, Web)
├── common/
│   ├── domain/BaseEntity   # createdAt, updatedAt, isDeleted, softDelete()
│   └── exception/          # BusinessException, ErrorCode(enum), GlobalExceptionHandler
└── domain/
    ├── member/             # 회원 (USER / OWNER / ADMIN)
    ├── store/              # 식당 (PostGIS 위치, JSONB 필드)
    ├── waiting/            # 웨이팅 핵심 도메인
    │   ├── facade/         # WaitingLockFacade (Redisson 분산락)
    │   └── scheduler/      # CleanupScheduler (매일 04:00)
    ├── review/             # 리뷰 + AI 요약 (AIReviewService)
    ├── favorite/           # 즐겨찾기
    ├── notification/       # SSE(NotificationService) + FCM(FcmService)
    └── oauth/              # Google OAuth2 핸들러
```

---

## 3. Layer Rules

### Controller
- `@RestController`, URL 패턴: `/api/v1/{domain}s`
- 인증된 사용자 email은 `Authentication` 객체에서 꺼내 Service에 전달
- Request 유효성 검사는 `@Valid` + DTO 어노테이션으로 처리
- **절대 비즈니스 로직을 Controller에 작성하지 말 것**

### Service
- 클래스 레벨 `@Transactional(readOnly = true)` 선언, 쓰기 메서드에만 `@Transactional` 추가
- 모든 예외는 `BusinessException(ErrorCode.XXX)` 형식으로 throw
- `RuntimeException`, `IllegalArgumentException` 직접 사용 금지 — ErrorCode에 추가 후 사용

### Facade (분산락)
- **대기 등록(`registerWaiting`)은 반드시 `WaitingLockFacade`를 통해 호출**
- Controller는 `WaitingService` 직접 주입 금지, `WaitingLockFacade` 사용
- 락 키 패턴: `"waiting:store:{storeId}"`
- tryLock 설정: 대기 10초, 락 유지 5초

---

## 4. Entity Rules

### BaseEntity
- 모든 Entity는 `BaseEntity`를 상속
- `softDelete()` 호출로 `isDeleted = true` 설정 (DB에서 물리 삭제 금지)
- `@SQLRestriction("is_deleted = false")`는 Member 엔티티에만 적용됨 — Store도 softDelete 사용하지만 어노테이션 없음

### Member
- 역할: `UserRole.USER`, `UserRole.OWNER`, `UserRole.ADMIN`
- 회원 탈퇴(`withdraw()`): `isDeleted=true` + email/loginId에 `"deleted_{timestamp}_"` prefix 추가 (unique 제약 해제 목적)
- `noShowCount >= 3` → `isBlacklisted() == true` → 대기 등록 차단

### Store
- `location`: `geometry(point, 4326)` — `GeometryFactory(PrecisionModel(), 4326)`으로 Point 생성 (longitude 먼저, latitude 나중)
- `businessHours`: `Map<String, String>` JSONB — 키: 요일 소문자 3자리 (mon/tue/wed/thu/fri/sat/sun), 값: "HH:mm-HH:mm" 또는 "OFF"
- `menuItems`: `List<MenuItemVo>` JSONB
- `weeklyWaitingStats`: `Map<String, List<HourlyStatVo>>` JSON — 키: DayOfWeek.name() (MONDAY/TUESDAY/...)
- `averageWaiting` 기본값: 10 (분)
- **Store 삭제는 `store.softDelete()` 사용**

### Waiting
- `queueNumber`: 등록 시점의 순번 (이후 변경 안 됨), 실제 순위는 `ticketTime`으로 계산
- `ticketTime` 기준으로 순서 정렬 — 미루기(`postpone()`) 시 `ticketTime = LocalDateTime.now()` 갱신
- `postponedCount` 최대 2, 초과 시 `IllegalStateException` (entity 내부)
- Status: `WAITING → CALL → SEATED` (정상) / `CANCEL`, `NOSHOW` (이탈)

---

## 5. Business Logic Rules

### 대기 등록
- blacklisted 회원 차단 먼저 확인
- 동일 가게 WAITING 상태 중복 등록 차단
- `queueNumber = 현재 (WAITING+CALL) 수 + 1`
- `expectedWaitMin = queueNumber * store.averageWaiting`
- **등록 후 queueNumber == 3이면 즉시 SSE 알림 전송**

### 상위 3번째 알림 규칙
- WAITING/CALL 상태 취소·착석·노쇼·미루기 발생 시 `isTop3()` 확인 후 `checkAndNotifyThirdInLine()` 호출
- `waitingRepository.flush()` 후 알림 전송 (상태 변경 DB 반영 보장)
- SSE 이벤트명: `"waiting-alert"`

### 리뷰 AI 요약
- 리뷰 50개 미만이면 고정 문자열 반환 (AI 미호출)
- Redis 캐시 키: `"aiSummary::{storeId}"`
- 동적 TTL: 500개 이상=3일, 300+이상=5일, 100+이상=7일, 50+이상=10일

### 새벽 배치 (CleanupScheduler)
- cron: `"0 0 4 * * ?"` — 매일 새벽 4시
- 작업 순서: ① 잔존 WAITING/CALL → CANCEL 처리 + SSE 연결 종료 → ② 어제 이후 웨이팅 발생 가게만 통계 재집계

---

## 6. Security & Auth Rules

### JWT
- Access Token: email + role 클레임, 만료 86400000ms (설정값)
- Refresh Token: Redis에 `memberId` 키로 저장 (`RefreshTokenRepository`)
- 필터: `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter` 앞에 위치

### 권한 매핑
| HTTP Method | URL | 필요 역할 |
|---|---|---|
| POST | /api/v1/stores | OWNER |
| PUT/PATCH | /api/v1/stores/** | OWNER |
| DELETE | /api/v1/stores/** | ADMIN 또는 OWNER |
| PATCH | /api/v1/waitings/*/status | OWNER |
| GET | /api/v1/stores/** | 누구나 |
| /api/v1/auth/** | - | 누구나 |

### OAuth2
- Google 전용, 성공 시 `OAuth2SuccessHandler`에서 JWT 발급
- OAuth2 사용자 `provider = "google"`, 일반 가입 `provider = "general"`

---

## 7. Error Handling Rules

- 새 에러 추가 시 반드시 `ErrorCode` enum에 먼저 추가
- 코드 규칙: 공통=C, 인증=A, 회원=M, 가게=S, 웨이팅=W, 리뷰=R, 즐겨찾기=F, 기타=O + 3자리 숫자
- `GlobalExceptionHandler`가 `BusinessException`과 `MethodArgumentNotValidException` 처리
- **새 예외 타입 클래스를 만들지 말 것 — BusinessException + ErrorCode 조합 사용**

---

## 8. Notification Rules

### SSE (실시간 대기 알림)
- `NotificationService.emitters`: `ConcurrentHashMap<Long, SseEmitter>` — memberId 키
- SSE 타임아웃: 60분
- **대기 관련 상태 변경 후 알림 필요 시 `notificationService.sendToClient()` 또는 `closeConnection()` 호출**
- 착석/취소/노쇼 시 반드시 `closeConnection()` 호출

### FCM (푸시 알림)
- `FcmService`에서 Firebase Admin SDK로 발송
- `Member.fcmToken` 저장, `updateFcmToken()` 메서드로 갱신

---

## 9. Database Rules

### PostGIS
- Point 생성: `new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(longitude, latitude))` — **경도(lng) 먼저, 위도(lat) 나중**
- 반경 검색: `StoreRepository.findStoresWithinRadius(lat, lng, radiusInMeters)` 사용

### JSONB 필드
- `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` 조합 사용
- VO 클래스는 `src/main/java/.../entity/` 에 위치 (`MenuItemVo`, `HourlyStatVo`)

### Native Query
- 통계 집계는 `WaitingRepository.findWaitingStatsByStoreId()` native query 사용 (DB pushdown)
- `WaitingStatProjection` 인터페이스로 결과 매핑

### DDL
- `application.yml`: `ddl-auto: create` — **로컬 개발 환경 전용, 프로덕션에서 변경 필요**
- 로컬 PostgreSQL 포트: **5433** (Docker는 5432)

---

## 10. Testing Rules

- 테스트 위치: `src/test/java/OneTwo/SmartWaiting/{domain}/service/`
- JaCoCo 커버리지 제외 대상: `config/`, `common/exception/`, `dto/`, `entity/`, `*Application*`, `facade/`
- **커버리지 측정 대상: service 클래스**
- 테스트 실행: `./gradlew test`

---

## 11. Environment Variables

| 변수명 | 용도 |
|---|---|
| DB_USERNAME | PostgreSQL 사용자명 |
| DB_PASSWORD | PostgreSQL 비밀번호 |
| JWT_SECRET | JWT 서명 키 |
| ADMIN_SECRET_KEY | 관리자 회원가입 시크릿 |
| GOOGLE_CLIENT_ID | Google OAuth2 클라이언트 ID |
| GOOGLE_CLIENT_SECRET | Google OAuth2 클라이언트 시크릿 |
| GEMINI_API_KEY | Google Gemini AI API 키 |

---

## 12. Prohibited Actions

- **`RuntimeException` / `IllegalArgumentException` 직접 throw 금지** → `BusinessException(ErrorCode.XXX)` 사용
- **Controller에 비즈니스 로직 작성 금지** → Service 계층 사용
- **대기 등록 시 `WaitingService` 직접 호출 금지** → `WaitingLockFacade` 경유
- **물리 삭제(DELETE SQL) 금지** → `softDelete()` 사용
- **Point 생성 시 위도/경도 순서 혼동 금지** → `Coordinate(longitude, latitude)` 순서 준수
- **새 예외 클래스 생성 금지** → ErrorCode enum 항목 추가 후 BusinessException 사용
- **SSE 상태 변경 후 flush() 없이 알림 전송 금지** → `waitingRepository.flush()` 후 알림 호출
- **리뷰 AI 요약 Redis 캐시 TTL 하드코딩 금지** → 리뷰 개수 기반 동적 TTL 로직 유지
