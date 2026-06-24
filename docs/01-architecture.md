# 01. 아키텍처 & 공통 인프라

> 레이어드 아키텍처, 패키지 구조, 모든 도메인이 공유하는 공통 기반 코드

---

## 1. 레이어드 아키텍처

SmartWaiting은 전형적인 **계층형 아키텍처**를 따르되, 동시성이 중요한 웨이팅 도메인에만 **Facade 계층**을 추가했습니다.

```
Controller   ──  HTTP 요청/응답, DTO 검증(@Valid), Principal에서 인증 사용자 추출
    │
   (Facade)  ──  [웨이팅 전용] Redisson 분산락 획득/해제
    │
Service      ──  비즈니스 로직, 트랜잭션 경계(@Transactional), 도메인 규칙 검증
    │
Repository   ──  Spring Data JPA, 네이티브 쿼리(PostGIS/JSONB), Projection
    │
Entity       ──  도메인 모델, 상태 변경 메서드, BaseEntity 상속(soft delete)
```

### 계층별 책임 원칙
- **Controller**: 비즈니스 로직 없음. `principal.getName()`(이메일)을 Service에 전달만 함
- **Service**: `@Transactional(readOnly = true)`를 클래스에 기본 적용, 쓰기 메서드만 `@Transactional` 오버라이드
- **Entity**: setter 금지. `changeStatus()`, `postpone()`, `updateRatingOnCreate()` 같은 **의미 있는 도메인 메서드**로만 상태 변경 → 더티 체킹으로 자동 저장

---

## 1-1. Facade 계층을 왜 두었나

위 계층도에서 `(Facade)`만 괄호로 묶인 이유는, **모든 도메인이 아니라 웨이팅 등록에만** 선택적으로 끼운 계층이기 때문입니다. 현재 `WaitingLockFacade` 하나만 존재하며, 웨이팅 등록(`POST /api/v1/waitings`)만 이 계층을 거치고 나머지 API는 Controller → Service로 직행합니다.

### 도입 배경 — 동시성 제어는 "별개의 관심사"

웨이팅 등록은 여러 손님이 같은 가게에 동시에 몰릴 수 있어 **분산락으로 직렬화**해야 합니다. 그런데 락을 획득·해제하는 코드는 성격이 애매합니다.

- 순번 계산·검증 같은 **비즈니스 로직**도 아니고,
- DB 트랜잭션 경계(`@Transactional`)도 아닙니다.

만약 이 락 코드를 `WaitingService` 안에 그대로 넣으면 두 가지 문제가 생깁니다.

1. **책임이 흐려진다** — Service가 "비즈니스 규칙"과 "동시성 제어"라는 두 역할을 동시에 떠안음.
2. **락과 트랜잭션의 순서가 위태로워진다** — `@Transactional` 메서드 *안에서* 락을 잡으면 "트랜잭션 시작 → 락 획득 → ... → 락 해제 → 트랜잭션 커밋" 순서가 되어, **락을 푼 뒤 커밋되기 전 찰나에** 다른 스레드가 들어와 직전 상태를 읽을 수 있습니다(락이 트랜잭션을 완전히 감싸지 못함).

### 해결 — 락을 트랜잭션 바깥에서 감싸는 얇은 계층

```
Controller → WaitingLockFacade(락 획득) → WaitingService(@Transactional) → 락 해제
                └─────────── 락이 트랜잭션 전체를 바깥에서 감쌈 ───────────┘
```

`WaitingLockFacade`는 "**락 획득 → Service 호출 → 락 해제**"만 담당하고, `WaitingService.registerWaiting()`은 순수 비즈니스 로직(검증·순번 계산·저장)만 갖습니다. 락이 트랜잭션 메서드를 **바깥에서** 감싸므로, 커밋이 끝나 변경이 DB에 확정된 뒤에야 락이 풀립니다.

### 그래서 무엇이 좋아졌나

| 효과 | 설명 |
|---|---|
| **① 관심사 분리** | Service는 "무엇을 검증하고 저장할지"에만 집중. 동시성 제어는 Facade가 전담 |
| **② 정합성 보장** | 락이 트랜잭션을 완전히 포함 → "락 해제 ≥ 커밋 완료" 순서가 보장되어 순번 중복·만석 초과를 원천 차단 |
| **③ 테스트 용이** | `WaitingService`를 락 없이 단독 단위테스트 가능. 실제로 JaCoCo 설정도 `**/facade/**`를 커버리지 측정에서 제외(§5)해, 로직 없는 락 계층과 비즈니스 로직을 분리 |
| **④ 선택적 적용** | 동시성이 중요한 등록에만 적용. 취소·미루기·조회 등은 Facade 없이 Service 직행 → 불필요한 오버헤드 없음 |

> 분산락의 구체적 동작(락 키 설계, `tryLock` 대기/보유 시간, 데드락 방지)은 [03-waiting](./03-waiting.md) §3에서 자세히 다룹니다.

---

## 2. 패키지 구조

```
OneTwo.SmartWaiting
├── SmartWaitingApplication.java     # 진입점 (@SpringBootApplication)
│
├── auth/                            # 인증 (도메인과 분리된 최상위)
│   ├── controller/  AuthController
│   ├── service/     AuthService, RefreshTokenService
│   ├── entity/      RefreshToken
│   └── dto/         SignUp/SignIn/Reissue ...
│
├── config/                          # 전역 설정
│   ├── SecurityConfig               # 보안 필터 체인, 권한 매핑
│   ├── JwtTokenProvider             # JWT 생성/검증
│   ├── JwtAuthenticationFilter      # 요청마다 토큰 인증
│   ├── RedissonConfig               # 분산락 클라이언트
│   ├── FirebaseConfig               # FCM 초기화
│   ├── S3Config                     # AWS S3 클라이언트
│   ├── JpaConfig                    # JPA Auditing 활성화
│   ├── WebConfig / SwaggerConfig
│
├── common/                          # 공통 기반
│   ├── domain/      BaseEntity      # 공통 PK·타임스탬프·soft delete
│   └── exception/   BusinessException, ErrorCode, GlobalExceptionHandler ...
│
└── domain/                          # 비즈니스 도메인 (도메인별 수직 분할)
    ├── member/      회원
    ├── store/       식당 + 통계
    ├── waiting/     웨이팅(핵심) + facade + scheduler
    ├── review/      리뷰 + AI 요약
    ├── favorite/    즐겨찾기
    ├── notification/ SSE + FCM + scheduler
    ├── upload/      이미지 업로드
    └── oauth/       소셜 로그인
```

### 구조적 특징
- **도메인별 수직 분할**: 각 도메인이 `controller/service/repository/entity/dto`를 자체 보유 → 응집도 높고 도메인 간 결합 낮음
- **auth를 domain 밖에 배치**: 인증은 특정 비즈니스 도메인이 아닌 횡단 관심사이므로 분리
- **공통 관심사는 common·config로 집약**

---

## 3. 공통 엔티티 — BaseEntity

모든 엔티티(`Member`, `Store`, `Waiting`, `Review`, `Favorite`)가 상속하는 베이스 클래스입니다.

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreatedDate     private LocalDateTime createdAt;   // 자동 기록
    @LastModifiedDate private LocalDateTime updatedAt;  // 자동 갱신
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    public Long softDelete() {              // 물리 삭제 대신 플래그만 변경
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        return this.id;
    }
    public void restore() { ... }
}
```

### 제공하는 3가지 공통 기능

**① 공통 PK & 식별자 전략**
- `IDENTITY` 전략(PostgreSQL `SERIAL`/auto-increment)

**② 자동 감사(Auditing)**
- `JpaConfig`의 `@EnableJpaAuditing` + `AuditingEntityListener`가 협력
- 엔티티 저장/수정 시 `createdAt`/`updatedAt`을 **자동** 기록
- 활용 예: 리뷰 48시간 제한 검증(`waiting.getUpdatedAt()`), 리뷰 요청 스케줄러(착석 시각 기준)

**③ Soft Delete (물리 삭제 금지)**
- 데이터 이력 보존을 위해 `DELETE` 대신 `isDeleted=true`로 처리
- **`Member` 엔티티는 `@SQLRestriction("is_deleted = false")`** 로 조회 시 자동 필터링
  ```java
  @SQLRestriction("is_deleted = false")
  public class Member extends BaseEntity { ... }
  ```
- 회원 탈퇴 시에는 unique 충돌 방지를 위해 이메일·로그인ID를 마스킹:
  ```java
  public void withdraw(){
      super.softDelete();
      this.email = "deleted_" + System.currentTimeMillis() + "_" + this.email;
      ...
  }
  ```

> ⚠️ **제약**: 물리 삭제(`repository.delete()`) 금지가 원칙. 단, 현재 리뷰·즐겨찾기는 하드 삭제를 사용 중(추후 개선 여지).

---

## 4. 통일된 예외 처리

### 4.1 설계 원칙: "예외 클래스는 단 하나"

새 예외 클래스를 만들지 않고 **`BusinessException` 하나 + `ErrorCode` enum 항목 추가**로 모든 비즈니스 예외를 표현합니다.

```java
// 예외 발생
throw new BusinessException(ErrorCode.WAITING_QUEUE_FULL);
```

```java
// ErrorCode enum — HTTP 상태 + 코드 + 메시지를 한 곳에서 관리
public enum ErrorCode {
    WAITING_QUEUE_FULL(HttpStatus.CONFLICT, "W007", "최대 대기 팀 수에 도달..."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "식당을 찾을 수 없습니다."),
    ...
}
```

**장점**: 모든 에러가 한 enum에 모여 응답 형식이 일관되고, 신규 에러 추가가 1줄로 끝남.

### 4.2 에러 코드 체계 (도메인별 접두어)

| 접두어 | 도메인 | 예시 |
|---|---|---|
| `C` | 공통(Common) | `C001` 잘못된 입력, `C002` 서버 오류 |
| `A` | 인증(Auth) | `A003` 아이디/비번 불일치, `A004` 토큰 무효 |
| `M` | 회원(Member) | `M001` 회원 없음, `M002` 이메일 중복 |
| `S` | 식당(Store) | `S001` 식당 없음, `S002` 점주 권한 없음 |
| `W` | 웨이팅(Waiting) | `W001`~`W007` (없음/중복/미루기한도/순번오류/접수중단/만석) |
| `R` | 리뷰(Review) | `R001`~`R005` (없음/중복/시간초과/권한/미방문) |
| `F` | 즐겨찾기(Favorite) | `F001` 없음, `F002` 중복 |
| `U` | 업로드(Upload) | `U001`~`U004` (형식/크기/개수/실패) |
| `O` | 기타(Other) | `O001` 블랙리스트 |

### 4.3 전역 핸들러 — GlobalExceptionHandler

`@RestControllerAdvice`가 3가지 예외를 일관된 `ErrorResponse`(코드·메시지·요청 URI 포함)로 변환합니다.

| 핸들러 | 대상 | 처리 |
|---|---|---|
| `handleBusinessException` | `BusinessException` | ErrorCode의 status·code·message로 응답 (`WARN` 로그) |
| `handleValidationException` | `@Valid` 실패 | 첫 번째 검증 메시지 추출, `V001` 코드 부여 (400) |
| `handleException` | 그 외 모든 예외 | `INTERNAL_SERVER_ERROR`(C002)로 감싸 노출 차단 (`ERROR` 로그) |

---

## 5. 전역 설정(config) 요약

| 설정 클래스 | 역할 | 상세 문서 |
|---|---|---|
| `SecurityConfig` | 필터 체인, URL·Role 권한 매핑, OAuth2 연결 | [02-auth-security](./02-auth-security.md) |
| `JwtTokenProvider` / `JwtAuthenticationFilter` | JWT 생성·검증, 요청 인증 | [02-auth-security](./02-auth-security.md) |
| `RedissonConfig` | 단일 서버 Redisson 클라이언트(분산락) | [03-waiting](./03-waiting.md) |
| `FirebaseConfig` | `@PostConstruct`로 FCM SDK 초기화 | [04-notification](./04-notification.md) |
| `S3Config` | AWS S3 클라이언트 빈 | [05-review-ai](./05-review-ai.md) |
| `JpaConfig` | `@EnableJpaAuditing` (createdAt/updatedAt 자동화) | 본 문서 §3 |
| `SwaggerConfig` | OpenAPI 문서 + JWT 인증 헤더 설정 | — |

### 테스트 커버리지 정책 (build.gradle)
JaCoCo 리포트에서 **로직이 없는 계층은 커버리지 측정 제외**:
```gradle
exclude: ["**/config/**", "**/common/exception/**", "**/dto/**",
          "**/entity/**", "**/*Application*", "**/facade/**"]
```
→ 순수 비즈니스 로직(Service)에 집중해 커버리지를 측정.
