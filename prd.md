# SmartWaiting — Product Requirements Document

---

## 1. 제품 개요

### 1.1 서비스 소개

**SmartWaiting**은 음식점 대기 줄 관리 플랫폼이다. 고객은 현장에서 줄을 서지 않고 앱으로 대기를 등록하고 실시간 알림을 받을 수 있으며, 사장님은 태블릿/PC로 대기열을 효율적으로 관리할 수 있다. AI가 누적된 리뷰를 자동 요약하고, 과거 웨이팅 데이터를 바탕으로 요일·시간대별 혼잡도를 예측·제공한다.

### 1.2 핵심 가치

| 가치 | 내용 |
|---|---|
| 고객 편의 | 현장 대기 없이 원격으로 웨이팅 등록, 앱 알림으로 입장 시점 안내 |
| 사장님 효율 | 대기열 현황 한눈에 파악, 노쇼 자동 감지 및 블랙리스트 관리 |
| 데이터 기반 운영 | 요일/시간별 혼잡도 통계, AI 리뷰 요약으로 메뉴·서비스 개선 |
| 신뢰성 | 분산락으로 동시 등록 Race Condition 방지, 새벽 배치로 유령 웨이팅 자동 정리 |

### 1.3 대상 사용자

| 역할 | 설명 |
|---|---|
| **USER** (고객) | 음식점을 검색하고 웨이팅을 등록하는 일반 사용자 |
| **OWNER** (사장님) | 식당을 등록하고 대기열을 관리하는 음식점 운영자 |
| **ADMIN** (관리자) | 전체 서비스를 관리하는 운영 관리자 |

---

## 2. 기술 스택

| 영역 | 기술 | 버전 |
|---|---|---|
| 백엔드 | Spring Boot | 4.0.1 |
| 언어 | Java | 17 |
| 빌드 | Gradle | - |
| 데이터베이스 | PostgreSQL + PostGIS | 15 |
| 캐시 / 분산락 | Redis + Redisson | Redis:alpine, Redisson 3.27.0 |
| 인증 | Spring Security + JWT (jjwt 0.11.5) | - |
| 소셜 로그인 | Google OAuth2 | - |
| AI | Spring AI + Google Gemini 2.5 Flash | Spring AI BOM 2.0.0-M4 |
| 푸시 알림 | Firebase FCM (Admin SDK) | 9.2.0 |
| 실시간 알림 | SSE (Server-Sent Events) | - |
| API 문서 | Springdoc OpenAPI | 2.6.0 |
| 테스트 커버리지 | JaCoCo | 0.8.11 |
| 인프라 | Docker Compose (db + redis + app) | - |

---

## 3. 현재 구현된 기능

### 3.1 인증 (Auth) — `/api/v1/auth`

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 일반 회원가입 | `POST /signup` | USER 역할로 회원가입 |
| 사장님 회원가입 | `POST /owner/signup` | OWNER 역할로 회원가입 |
| 관리자 회원가입 | `POST /admin/signup` | `ADMIN_SECRET_KEY` 검증 후 ADMIN 역할 가입 |
| 로그인 | `POST /signin` | Access Token + Refresh Token 발급 |
| 관리자 로그인 | `POST /admin/signin` | 관리자 전용 로그인 |
| 토큰 재발급 | `POST /reissue` | Refresh Token으로 Access Token 재발급 |
| 로그아웃 | `POST /logout` | Redis에서 Refresh Token 삭제 |
| Google 소셜 로그인 | OAuth2 흐름 | Google 계정 연동, 성공 시 JWT 발급 |

**인증 방식**
- Access Token: `email + role` 클레임, 만료 24시간
- Refresh Token: Redis에 `memberId` 키로 저장, 만료 7일
- Stateless 세션, CSRF 비활성화

---

### 3.2 회원 (Member) — `/api/v1/members`

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 내 정보 조회 | `GET /me` | 로그인 사용자 정보 반환 |
| 내 정보 수정 | `PUT /me` | 이름(닉네임) 변경 |
| 비밀번호 변경 | `PATCH /me/password` | 현재 비밀번호 확인 후 변경 |
| 회원 탈퇴 | `DELETE /me` | Soft Delete (email/loginId에 `deleted_{timestamp}_` 접두사) |

**비즈니스 규칙**
- 노쇼 3회 이상 → `isBlacklisted() = true` → 웨이팅 등록 차단
- FCM Token 업데이트 지원 (`updateFcmToken()`)
- 탈퇴 시 unique 제약 해제 목적으로 email/loginId 변형

---

### 3.3 식당 (Store) — `/api/v1/stores`

| 기능 | 엔드포인트 | 권한 | 설명 |
|---|---|---|---|
| 식당 등록 | `POST /` | OWNER | PostGIS Point + JSONB 영업시간/메뉴 저장 |
| 식당 단건 조회 | `GET /{storeId}` | 누구나 | 상세 정보 + 주간 혼잡도 통계 포함 |
| 내 주변 식당 검색 | `GET /nearby` | 누구나 | PostGIS 반경 검색 (기본 1000m) |
| 이름/카테고리 검색 | `GET /search` | 누구나 | 슬라이스 페이지네이션 |
| 식당 정보 수정 | `PUT /{storeId}` | OWNER(본인) | 위치, 영업시간, 메뉴 등 전체 수정 |
| 식당 삭제 | `DELETE /{storeId}` | OWNER/ADMIN | Soft Delete |

**식당 카테고리**: 한식, 중식, 일식, 양식, 카페/디저트, 기타

**JSONB 필드**
- `businessHours`: `{ "mon": "11:00-22:00", "sun": "OFF" }` 형식
- `menuItems`: `[{ "name": "...", "price": ... }]` 형식
- `weeklyWaitingStats`: `{ "MONDAY": [{ "hour": 12, "avgTeams": 3.5, "avgWaitMin": 25.0 }, ...] }` 형식

---

### 3.4 웨이팅 (Waiting) — `/api/v1/waitings`

| 기능 | 엔드포인트 | 권한 | 설명 |
|---|---|---|---|
| 웨이팅 등록 | `POST /` | USER | 분산락 경유, 블랙리스트·중복 체크 |
| 웨이팅 취소 | `PATCH /{id}/cancel` | USER(본인) | 취소 후 3번째 대기자 알림 |
| 웨이팅 미루기 | `PATCH /{id}/postpone` | USER(본인) | 최대 2회, ticketTime 갱신으로 순서 변경 |
| 상태 변경 | `PATCH /{id}/status` | OWNER | CALL / SEATED / NOSHOW 처리 |
| 내 웨이팅 목록 | `GET /my` | USER | 전체 이력 + 현재 대기 순번 실시간 계산 |
| 가게 대기열 조회 | `GET /store/{storeId}` | OWNER(본인) | 상태별 대기열 목록 |

**웨이팅 상태 흐름**
```
WAITING → CALL → SEATED (정상 입장)
WAITING → CANCEL         (고객 취소)
WAITING → NOSHOW         (미응답, 노쇼 카운트 +1)
CALL    → NOSHOW         (호출 후 미응답)
```

**핵심 비즈니스 로직**
- `queueNumber`: 등록 시 부여, 이후 불변 (표시용)
- 실제 순서: `ticketTime` 기준 정렬 — 미루기 시 갱신
- `expectedWaitMin = 내 앞 팀 수 × averageWaiting`
- 상위 3번째 대기자에게 자동 SSE 알림 ("매장 앞으로 이동해 주세요")
- Redisson 분산락: 키 `"waiting:store:{storeId}"`, tryLock(10초, 유지 5초)

---

### 3.5 실시간 알림 (Notification) — `/api/v1/notifications`

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| SSE 구독 | `GET /subscribe/{memberId}` | 60분 유지, `waiting-alert` 이벤트 |

**알림 트리거**
| 이벤트 | 수신자 | 메시지 |
|---|---|---|
| 웨이팅 등록 (본인이 3번째) | 해당 고객 | "대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요." |
| 상위 3명 변동 (취소/착석/노쇼/미루기) | 새로운 3번째 고객 | "대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요." |
| 사장님 호출 (CALL) | 해당 고객 | "사장님이 호출하셨습니다! 매장으로 입장해 주세요." |
| 영업 종료 새벽 배치 | 잔존 대기 고객 전원 | "영업 시간이 종료되어 대기가 자동 취소되었습니다." |

**FCM**: Firebase Admin SDK, `Member.fcmToken` 저장, 앱 오프라인 시 푸시 발송

---

### 3.6 리뷰 (Review) — `/api/v1/reviews`

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 리뷰 작성 | `POST /` | waitingId 기반, SEATED 상태 + 48시간 이내만 허용 |
| 가게 리뷰 조회 | `GET /store/{storeId}` | 최신순, Slice 페이지네이션 |
| 내 리뷰 목록 | `GET /my` | 최신순, Slice 페이지네이션 |
| 리뷰 삭제 | `DELETE /{reviewId}` | 본인 작성 리뷰만 삭제 |
| AI 리뷰 요약 | `GET /summary/{storeId}` | Redis 캐시, Gemini 2.5 Flash 3줄 요약 |

**리뷰 작성 조건**: 실제 웨이팅 후 착석(SEATED)한 고객만, 착석 후 48시간 이내, 웨이팅 1건당 리뷰 1개

**AI 요약 캐시 전략**
| 리뷰 수 | TTL |
|---|---|
| 500개 이상 | 3일 |
| 300개 이상 | 5일 |
| 100개 이상 | 7일 |
| 50개 이상 | 10일 |
| 50개 미만 | AI 미호출, 고정 문자열 반환 |

---

### 3.7 즐겨찾기 (Favorite) — `/api/v1/favorites`

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 즐겨찾기 추가 | `POST /` | 식당 찜하기 |
| 즐겨찾기 취소 | `DELETE /` | 찜 해제 |
| 내 즐겨찾기 목록 | `GET /my` | Slice 페이지네이션 |

---

### 3.8 통계 및 배치 (Statistics & Scheduler)

**요일·시간별 혼잡도 통계**
- 최근 30일 웨이팅 데이터 기반
- DB 내 집계 (PostgreSQL native query, `EXTRACT(ISODOW)`, `EXTRACT(HOUR)`)
- 결과: 각 요일·시간대별 평균 대기 팀 수 + 평균 대기 시간
- 식당 조회 시 `weeklyWaitingStats` 필드로 반환

**새벽 배치 스케줄러** (매일 04:00 자동 실행)
1. 잔존 WAITING/CALL 상태 웨이팅 → CANCEL 처리 + SSE 연결 종료 + 안내 메시지 발송
2. 어제 이후 웨이팅 발생 가게만 선별하여 통계 재집계 (불필요한 재계산 방지)

---

## 4. 향후 개발 계획

> 우선순위: P0(필수) → P1(중요) → P2(선택)

---

### Phase 1 — 사용성 핵심 개선 (P0)

#### 4.1 소셜 로그인 확대

**배경**: 현재 Google OAuth2만 지원. 국내 사용자 비중을 감안하면 카카오/네이버 추가가 필수.

**구현 계획**
- `CustomOAuth2UserService`에 `provider` 분기 처리 추가 (kakao, naver)
- 카카오: REST API key + 인가코드 방식
- 네이버: Client ID/Secret + 상태 토큰 방식
- `Member.provider` 필드 활용, 기존 general/google 흐름과 동일 패턴 유지

---

#### 4.2 입장 직전 알림 (1번째 대기자 알림)

**배경**: 현재 3번째 대기자에게만 알림 발송. 1번째가 됐을 때 알림이 없어 사용자가 능동적으로 확인해야 함.

**구현 계획**
- `checkAndNotifyThirdInLine()` 패턴과 동일하게 `checkAndNotifyFirstInLine()` 추가
- 발동 조건: 2번째 대기자 취소/착석/노쇼 시 새로운 1번째에게 FCM + SSE 동시 발송
- 메시지: "곧 입장하실 차례입니다! 매장 앞에 대기해 주세요."

---

#### 4.3 웨이팅 일시 중단 / 재개 기능

**배경**: 사장님이 갑작스러운 사유(재료 소진, 임시 휴무)로 웨이팅을 일시 중단해야 하는 상황. 현재는 불가.

**구현 계획**
- `Store`에 `isAcceptingWaiting: Boolean` 필드 추가
- `PATCH /api/v1/stores/{storeId}/waiting-status` 엔드포인트 (OWNER 권한)
- `WaitingService.registerWaiting()` 진입 시 해당 필드 체크
- `STORE_NOT_ACCEPTING_WAITING` ErrorCode 추가

---

#### 4.4 최대 대기 팀 수 설정

**배경**: 주방 용량 초과 방지. 사장님이 오늘의 최대 대기 수용 인원을 직접 설정할 수 있어야 함.

**구현 계획**
- `Store`에 `maxWaitingCount: Integer` 필드 추가 (null = 무제한)
- 웨이팅 등록 시 현재 WAITING+CALL 수와 비교
- 초과 시 `WAITING_QUEUE_FULL` ErrorCode 반환

---

### Phase 2 — 고객 경험 향상 (P1)

#### 4.5 웨이팅 현황 실시간 폴링 API

**배경**: 현재 내 웨이팅 목록 조회 시 `teamsAhead`를 계산하지만, 실시간으로 순번이 변할 때 자동 갱신이 안 됨. SSE 알림은 3번째가 됐을 때만 발생.

**구현 계획**
- `GET /api/v1/waitings/{waitingId}/status` 단건 상태 조회 엔드포인트 추가
- 응답: `{ status, queueNumber, teamsAhead, expectedWaitMin }` — 조회 시점 기준 실시간 계산
- 클라이언트에서 30초 주기 폴링 또는 SSE 이벤트 수신 시 갱신

---

#### 4.6 리뷰 이미지 첨부

**배경**: 텍스트 리뷰만 가능. 음식 사진 첨부 시 리뷰 신뢰도 및 참고 가치 향상.

**구현 계획**
- AWS S3 또는 GCS Bucket 연동 (pre-signed URL 방식)
- `Review`에 `imageUrls: List<String>` JSONB 필드 추가
- 파일 업로드 전용 엔드포인트: `POST /api/v1/uploads/review-image`
- 최대 3장, 허용 확장자: jpg/png/webp, 크기 제한: 장당 5MB

---

#### 4.7 리뷰 평점 집계 및 식당 평균 별점

**배경**: 리뷰 별점(`rating`)이 있지만 식당 응답에 평균 별점이 반영되지 않음.

**구현 계획**
- `Store`에 `averageRating: Double`, `reviewCount: Integer` 캐싱 필드 추가
- 리뷰 작성/삭제 시 `@TransactionalEventListener`로 비동기 집계 갱신
- 또는 `ReviewRepository`에 `@Query("SELECT AVG(rating) FROM Review WHERE store.id = :storeId")`로 조회 시 계산
- `StoreResponseDto`에 `averageRating`, `reviewCount` 추가

---

#### 4.8 블랙리스트 해제 / 이의신청

**배경**: 노쇼 3회 도달 시 영구 차단. 본인 실수가 아닌 경우(서비스 오류 등) 이의신청 수단 없음.

**구현 계획**
- ADMIN 전용 엔드포인트: `PATCH /api/v1/admin/members/{memberId}/reset-noshow`
- `noShowCount` 리셋 또는 지정 횟수 차감
- 이의신청 기록 테이블: `BlacklistAppeal` 엔티티 추가 (PENDING/APPROVED/REJECTED)

---

### Phase 3 — 사장님 운영 도구 (P1)

#### 4.9 영업 분석 대시보드 API

**배경**: `weeklyWaitingStats`는 식당 조회 시 반환되지만, 사장님 전용 분석 뷰가 없음. 더 다양한 인사이트 필요.

**구현 계획**
- `GET /api/v1/stores/{storeId}/analytics` (OWNER 권한)
- 응답 내용:
  - 최근 7일 / 30일 총 웨이팅 수
  - 평균 대기 시간 추이
  - 노쇼율 (NOSHOW 수 / 총 웨이팅 수)
  - 가장 혼잡한 요일·시간대 TOP 3
  - 평균 인원 수(headCount) 분포

---

#### 4.10 예상 대기 시간 동적 조정

**배경**: 현재 `expectedWaitMin = 순번 × averageWaiting`이며 `averageWaiting`은 사장님이 수동 설정. 실제 착석 소요 시간을 반영하지 못함.

**구현 계획**
- `CleanupScheduler` 또는 별도 스케줄러에서 주기적으로 최근 10건의 실제 착석 소요 시간(CALL → SEATED 시간 차) 계산
- `Store.averageWaiting` 자동 업데이트
- 수동 설정값 우선 여부를 `Store.manualAverageWaiting: Boolean`으로 제어

---

#### 4.11 멀티 스토어 지원 (사장님 복수 가게 관리)

**배경**: 현재 `Store.ownerId` 단방향 연결. 프랜차이즈 사장님은 여러 지점을 운영.

**구현 계획**
- `OWNER → Store` 관계를 `@OneToMany`로 확장 (현재는 `ownerId` Long 저장)
- `GET /api/v1/stores/my` 엔드포인트: 내 모든 가게 목록 조회 (OWNER 권한)
- 가게별 대기열 현황 통합 뷰 응답

---

### Phase 4 — 인프라 및 안정성 (P2)

#### 4.12 다중 서버 SSE 지원 (Redis Pub/Sub)

**배경**: 현재 `ConcurrentHashMap<Long, SseEmitter>`는 단일 인스턴스 내 메모리 저장. 서버를 스케일 아웃하면 다른 인스턴스에 연결된 클라이언트에게 SSE를 발송할 수 없음.

**구현 계획**
- SSE 알림 발송 시 Redis Pub/Sub 채널 `"sse:member:{memberId}"` 에 메시지 발행
- 각 서버 인스턴스가 `@PostConstruct`로 채널 구독, 로컬에 해당 emitter가 있으면 발송
- 기존 `NotificationService` 인터페이스 유지, 내부 구현만 교체

---

#### 4.13 모니터링 연동 (Prometheus + Grafana)

**배경**: 현재 운영 중 성능 병목, 에러율, DB 쿼리 시간 등을 관찰할 수단이 없음.

**구현 계획**
- `spring-boot-actuator` + `micrometer-registry-prometheus` 의존성 추가
- 핵심 메트릭: 웨이팅 등록 TPS, 분산락 대기 시간, Redis 캐시 히트율, API 응답 시간 P99
- Grafana 대시보드: 실시간 대기열 현황, 시간당 트랜잭션 수
- `docker-compose.yml`에 prometheus + grafana 서비스 추가

---

#### 4.14 테스트 커버리지 확대

**배경**: JaCoCo 설정은 있으나 Service 레이어 테스트가 부분적.

**목표 커버리지**: Service 클래스 라인 커버리지 80% 이상

**우선 구현 테스트**
- `WaitingServiceTest`: 블랙리스트 차단, 중복 등록 차단, 3번째 알림 로직, 미루기 제한
- `ReviewServiceTest`: 방문 검증(SEATED), 48시간 만료, 중복 리뷰 차단
- `StoreStatisticServiceTest`: 통계 집계 정확도 (빈 시간대 0 처리)
- `AIReviewServiceTest`: 동적 TTL 분기, 50개 미만 처리

---

#### 4.15 CI/CD 파이프라인

**배경**: 현재 수동 배포. GitHub Actions 기반 자동화 필요.

**구현 계획**
- `.github/workflows/ci.yml`:
  - PR 생성 시 `./gradlew test jacocoTestReport` 실행
  - 커버리지 80% 미달 시 PR merge 차단
- `.github/workflows/cd.yml`:
  - `main` 브랜치 push 시 Docker 이미지 빌드 → Container Registry 푸시 → 서버 배포
- 환경변수: GitHub Secrets로 관리

---

## 5. API 전체 목록 요약

| # | 도메인 | Method | Path | 권한 | 설명 |
|---|---|---|---|---|---|
| 1 | Auth | POST | /api/v1/auth/signup | - | 일반 회원가입 |
| 2 | Auth | POST | /api/v1/auth/signin | - | 로그인 |
| 3 | Auth | POST | /api/v1/auth/owner/signup | - | 사장님 회원가입 |
| 4 | Auth | POST | /api/v1/auth/admin/signup | - | 관리자 회원가입 |
| 5 | Auth | POST | /api/v1/auth/admin/signin | - | 관리자 로그인 |
| 6 | Auth | POST | /api/v1/auth/reissue | - | 토큰 재발급 |
| 7 | Auth | POST | /api/v1/auth/logout | 인증 | 로그아웃 |
| 8 | Member | GET | /api/v1/members/me | 인증 | 내 정보 조회 |
| 9 | Member | PUT | /api/v1/members/me | 인증 | 내 정보 수정 |
| 10 | Member | PATCH | /api/v1/members/me/password | 인증 | 비밀번호 변경 |
| 11 | Member | DELETE | /api/v1/members/me | 인증 | 회원 탈퇴 |
| 12 | Store | POST | /api/v1/stores | OWNER | 식당 등록 |
| 13 | Store | GET | /api/v1/stores/{storeId} | 누구나 | 식당 단건 조회 |
| 14 | Store | GET | /api/v1/stores/nearby | 누구나 | 내 주변 식당 검색 |
| 15 | Store | GET | /api/v1/stores/search | 누구나 | 이름/카테고리 검색 |
| 16 | Store | PUT | /api/v1/stores/{storeId} | OWNER | 식당 정보 수정 |
| 17 | Store | DELETE | /api/v1/stores/{storeId} | OWNER/ADMIN | 식당 삭제 |
| 18 | Waiting | POST | /api/v1/waitings | USER | 웨이팅 등록 |
| 19 | Waiting | PATCH | /api/v1/waitings/{id}/cancel | USER | 웨이팅 취소 |
| 20 | Waiting | PATCH | /api/v1/waitings/{id}/postpone | USER | 웨이팅 미루기 |
| 21 | Waiting | PATCH | /api/v1/waitings/{id}/status | OWNER | 상태 변경 |
| 22 | Waiting | GET | /api/v1/waitings/my | USER | 내 웨이팅 목록 |
| 23 | Waiting | GET | /api/v1/waitings/store/{storeId} | OWNER | 가게 대기열 조회 |
| 24 | Notification | GET | /api/v1/notifications/subscribe/{memberId} | 인증 | SSE 실시간 구독 |
| 25 | Review | POST | /api/v1/reviews | USER | 리뷰 작성 |
| 26 | Review | GET | /api/v1/reviews/store/{storeId} | 누구나 | 가게 리뷰 목록 |
| 27 | Review | GET | /api/v1/reviews/my | USER | 내 리뷰 목록 |
| 28 | Review | DELETE | /api/v1/reviews/{reviewId} | USER | 리뷰 삭제 |
| 29 | Review | GET | /api/v1/reviews/summary/{storeId} | 누구나 | AI 리뷰 요약 |
| 30 | Favorite | POST | /api/v1/favorites | USER | 즐겨찾기 추가 |
| 31 | Favorite | DELETE | /api/v1/favorites | USER | 즐겨찾기 취소 |
| 32 | Favorite | GET | /api/v1/favorites/my | USER | 내 즐겨찾기 목록 |

---

## 6. 향후 기능 개발 로드맵

```
Phase 1 (핵심 완성)
├── 4.1 카카오/네이버 소셜 로그인
├── 4.2 1번째 대기자 입장 직전 알림
├── 4.3 웨이팅 일시 중단/재개
└── 4.4 최대 대기 팀 수 설정

Phase 2 (고객 경험)
├── 4.5 웨이팅 현황 실시간 단건 조회
├── 4.6 리뷰 이미지 첨부
├── 4.7 식당 평균 별점 집계
└── 4.8 블랙리스트 이의신청

Phase 3 (사장님 도구)
├── 4.9 영업 분석 대시보드
├── 4.10 예상 대기 시간 동적 조정
└── 4.11 멀티 스토어 지원

Phase 4 (인프라)
├── 4.12 Redis Pub/Sub SSE 스케일 아웃
├── 4.13 Prometheus + Grafana 모니터링
├── 4.14 테스트 커버리지 80% 달성
└── 4.15 GitHub Actions CI/CD 파이프라인
```

---

## 7. 핵심 비즈니스 제약사항 (변경 불가)

| 제약 | 이유 |
|---|---|
| 웨이팅 등록은 반드시 `WaitingLockFacade` 경유 | Redisson 분산락으로 Race Condition 방지 |
| 물리 삭제 금지, `softDelete()` 사용 | 데이터 복원 가능성 및 이력 보존 |
| `Coordinate(longitude, latitude)` 순서 고정 | PostGIS WGS84 좌표계 (SRID 4326) 규격 |
| 모든 예외: `BusinessException(ErrorCode.XXX)` | 통일된 에러 응답 포맷 보장 |
| 새 예외 클래스 생성 금지 | `ErrorCode` enum 항목 추가로 대체 |
| SSE 알림 전 `waitingRepository.flush()` 필수 | 상태 변경 DB 반영 후 알림 전송 순서 보장 |
