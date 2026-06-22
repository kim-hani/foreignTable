# SmartWaiting — 개발 태스크 로드맵

> 마지막 업데이트: 2026-06-22  
> 우선순위: P0(필수) → P1(중요) → P2(인프라/선택)

---

## 즉시 처리

| # | 태스크 | 상태 |
|---|---|---|
| 1 | feat/firstNotification PR 생성 및 main 머지 | 🔲 pending |

**feat/firstNotification 구현 내용 (완료):**
- `WaitingService.checkAndNotifyFirstInLine()` / `isTop2()` 구현
- `FcmService.sendFirstInLinePush()` 구현
- `WaitingServiceTest` isTop2, 1등 알림 테스트 완료

**PR 절차:**
1. `git push --force-with-lease origin feat/firstNotification`
2. GitHub PR 생성 (`.claude/git-conventions.md` 형식)
3. Squash & Merge → main 반영

---

## Phase 1 — P0 핵심 완성

| # | 태스크 | 상태 |
|---|---|---|
| 4 | 웨이팅 일시 중단/재개 기능 | 🔲 pending |
| 5 | 최대 대기 팀 수 설정 기능 | 🔲 pending |
| 17 | FCM 토큰 갱신 API | 🔲 pending |

### #4 — 웨이팅 일시 중단/재개
- `Store` 엔티티에 `isAcceptingWaiting: Boolean` 필드 추가 (기본값 true)
- `PATCH /api/v1/stores/{storeId}/waiting-status` (OWNER 권한)
- `WaitingService.registerWaiting()` 진입 시 체크
- `ErrorCode.STORE_NOT_ACCEPTING_WAITING` (S xxx) 추가

### #5 — 최대 대기 팀 수 설정
- `Store` 엔티티에 `maxWaitingCount: Integer` 필드 추가 (null = 무제한)
- 등록 시 현재 WAITING+CALL 수 비교
- `ErrorCode.WAITING_QUEUE_FULL` (W xxx) 추가

### #17 — FCM 토큰 갱신 API
- `PATCH /api/v1/members/me/fcm-token` 엔드포인트
- `FcmTokenUpdateRequestDto` + `MemberService.updateFcmToken()` 추가
- Member 엔티티에 `updateFcmToken()` 메서드 이미 존재, 컨트롤러만 없음

> ✅ **이미 구현 완료 (태스크 제거됨)**
> - 카카오 소셜 로그인 — `CustomOAuth2UserService` + `application.yml` 완전 구현
> - 네이버 소셜 로그인 — 동일
> - 리뷰 요청 FCM 스케줄러 — `ReviewNotificationScheduler` (착석 1시간 후 자동 발송)

---

## Phase 2 — P1 고객 경험

| # | 태스크 | 상태 |
|---|---|---|
| 6 | 웨이팅 현황 실시간 단건 조회 API | 🔲 pending |
| 7 | 리뷰 이미지 첨부 기능 (S3/GCS 연동) | 🔲 pending |
| 8 | 식당 평균 별점 집계 및 응답 반영 | 🔲 pending |
| 9 | 블랙리스트 이의신청 및 ADMIN 해제 기능 | 🔲 pending |

### #6 — 웨이팅 현황 실시간 단건 조회
- `GET /api/v1/waitings/{waitingId}/status` (USER, 본인만)
- 응답: `{ status, queueNumber, teamsAhead, expectedWaitMin }` — 조회 시점 실시간 계산
- 클라이언트 30초 폴링 또는 SSE 수신 시 갱신 용도

### #7 — 리뷰 이미지 첨부
- AWS S3 또는 GCS 연동 (pre-signed URL)
- `Review` 엔티티에 `imageUrls: List<String>` JSONB 필드 추가
- `POST /api/v1/uploads/review-image` (최대 3장, jpg/png/webp, 장당 5MB)

### #8 — 식당 평균 별점 집계
- `Store` 엔티티에 `averageRating: Double`, `reviewCount: Integer` 추가
- 리뷰 작성/삭제 시 `@TransactionalEventListener`로 비동기 갱신
- `StoreResponseDto`에 필드 추가

### #9 — 블랙리스트 이의신청 및 ADMIN 해제
- `BlacklistAppeal` 엔티티 (PENDING/APPROVED/REJECTED, BaseEntity 상속)
- `POST /api/v1/appeals` — USER 이의신청
- `GET /api/v1/admin/appeals` — ADMIN 목록 조회
- `PATCH /api/v1/admin/members/{memberId}/reset-noshow` — ADMIN noShowCount 리셋

---

## Phase 3 — P1 사장님 도구

| # | 태스크 | 상태 |
|---|---|---|
| 10 | 영업 분석 대시보드 API | 🔲 pending |
| 11 | 예상 대기 시간 동적 자동 조정 | 🔲 pending |
| 12 | 멀티 스토어 지원 | 🔲 pending |

### #10 — 영업 분석 대시보드
- `GET /api/v1/stores/{storeId}/analytics` (OWNER, 본인 가게만)
- 최근 7일/30일 웨이팅 수, 평균 대기 시간, 노쇼율, 혼잡 TOP 3, headCount 분포

### #11 — 예상 대기 시간 동적 조정
- `Store`에 `manualAverageWaiting: Boolean` 추가
- 스케줄러에서 최근 10건 CALL→SEATED 소요 시간 계산 → `averageWaiting` 자동 갱신
- `manualAverageWaiting=true`이면 사장님 수동값 우선 유지

### #12 — 멀티 스토어 지원
- `GET /api/v1/stores/my` (OWNER) — 내 모든 가게 목록
- 현재 `Store.ownerId(Long)` 구조 유지하며 쿼리만 추가
- SecurityConfig에 `/api/v1/stores/my` OWNER 권한 매핑 추가

---

## Phase 4 — P2 인프라 및 안정성

| # | 태스크 | 상태 |
|---|---|---|
| 13 | Redis Pub/Sub 기반 SSE 스케일 아웃 | 🔲 pending |
| 14 | Prometheus + Grafana 모니터링 연동 | 🔲 pending |
| 15 | 나머지 Service 레이어 테스트 커버리지 80% | 🔲 pending |
| 16 | GitHub Actions CI/CD 파이프라인 구축 | 🔲 pending |

### #13 — Redis Pub/Sub SSE 스케일 아웃
- 현재: `ConcurrentHashMap<Long, SseEmitter>` — 단일 인스턴스 내 메모리 저장
- 채널: `"sse:member:{memberId}"` 에 메시지 발행
- 각 인스턴스가 @PostConstruct에서 채널 구독, 로컬 emitter 존재 시 발송
- 기존 `NotificationService` 인터페이스 유지, 내부 구현만 교체

### #14 — Prometheus + Grafana 모니터링
- `spring-boot-actuator` + `micrometer-registry-prometheus` 의존성 추가
- 핵심 메트릭: 웨이팅 등록 TPS, 분산락 대기 시간, Redis 캐시 히트율, API P99
- `docker-compose.yml`에 prometheus, grafana 서비스 추가

### #15 — Service 테스트 커버리지 80%
> WaitingServiceTest는 feat/firstNotification에서 이미 포괄적으로 완성됨

작성 필요:
- `ReviewServiceTest`: SEATED 검증, 48시간 만료, 중복 리뷰 차단 (파일 있음, 내용 보강)
- `StoreServiceTest`: 생성/수정/삭제 권한, 반경 검색 (파일 있음)
- `FavoriteServiceTest`: 추가/취소/중복 차단 (파일 있음)
- `AIReviewServiceTest`: 동적 TTL 분기, 50개 미만 처리
- `StoreStatisticServiceTest`: 통계 집계, 빈 시간대 0 처리

확인: `./gradlew test jacocoTestReport`

### #16 — GitHub Actions CI/CD
- `.github/workflows/ci.yml`: PR 시 테스트 + JaCoCo 커버리지 80% 미달 차단
- `.github/workflows/cd.yml`: main push 시 Docker 빌드 → Registry → 서버 배포
- GitHub Secrets에 환경변수 등록 (DB_PASSWORD, JWT_SECRET 등)

---

## 현재 구현 완료 기능 요약

| 도메인 | 기능 | 비고 |
|---|---|---|
| Auth | 일반/사장님/관리자 회원가입, 로그인, JWT, 토큰 재발급, 로그아웃 | ✅ |
| Auth | Google/카카오/네이버 OAuth2 소셜 로그인 | ✅ |
| Member | 내 정보 조회/수정, 비밀번호 변경, 회원 탈퇴 | ✅ |
| Store | 등록/조회/수정/삭제, 반경 검색, 이름·카테고리 검색 | ✅ |
| Waiting | 등록(분산락), 취소, 미루기, 상태 변경, 내 목록, 가게 대기열 조회 | ✅ |
| Notification | SSE 구독, 3번째 대기자 알림, **1번째 대기자 FCM+SSE 알림** | ✅ (PR 대기) |
| Review | 작성, 조회, 삭제, AI 요약(Gemini), 리뷰 요청 FCM 스케줄러 | ✅ |
| Favorite | 즐겨찾기 추가/취소/목록 | ✅ |
| Statistics | 요일·시간별 혼잡도 집계, 새벽 배치(유령 웨이팅 정리) | ✅ |
