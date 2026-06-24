# SmartWaiting 기술 문서

> 음식점 대기 줄 관리 플랫폼 — 실시간 웨이팅, 알림(SSE+FCM), AI 리뷰 요약
> Spring Boot 4.0.1 / Java 17 / PostgreSQL(PostGIS) / Redis

이 문서는 SmartWaiting 백엔드의 **모든 기능에 대한 작동 원리, 파이프라인, 기술 선택 이유**를 정리한 기술 명세서입니다.

---

## 📚 문서 목차

| # | 문서 | 내용 |
|---|---|---|
| 0 | **README.md** (현재 문서) | 프로젝트 개요, 전체 기술 스택, 시스템 아키텍처 |
| 1 | [01-architecture.md](./01-architecture.md) | 레이어드 아키텍처, 패키지 구조, 공통 인프라(BaseEntity·예외처리·Auditing) |
| 2 | [02-auth-security.md](./02-auth-security.md) | JWT 인증/인가, RefreshToken, OAuth2 소셜 로그인, 회원 관리 |
| 3 | [03-waiting.md](./03-waiting.md) | **핵심 도메인** — 분산락 웨이팅 등록, 상태 머신, 순번·예상시간 계산, 미루기, 블랙리스트 |
| 4 | [04-notification.md](./04-notification.md) | **알림 기능** — SSE 실시간 알림, FCM 푸시, 1·3번째 알림, 리뷰요청 스케줄러 |
| 5 | [05-review-ai.md](./05-review-ai.md) | 리뷰 CRUD, **AI 3줄 요약(Gemini)**, 이미지 업로드(S3), 평균 별점 집계 |
| 6 | [06-store-statistics.md](./06-store-statistics.md) | 식당 관리, **PostGIS 위치 검색**, JSONB 활용, 혼잡도 통계 배치, 즐겨찾기 |
| 7 | [07-cicd.md](./07-cicd.md) | **CI/CD** — GitHub Actions 자동 빌드·배포, Docker 멀티스테이지, Docker Compose |
| 8 | [08-aws-infrastructure.md](./08-aws-infrastructure.md) | **AWS 인프라** — VPC·서브넷, EC2·ECR·ALB·Route53·RDS·ElastiCache·S3·모니터링 |

---

## 1. 프로젝트 개요

SmartWaiting은 오프라인 음식점의 **대기 줄(웨이팅)을 디지털화**하는 플랫폼입니다. 세 종류의 사용자가 등장합니다.

| 역할 | 설명 | 주요 행위 |
|---|---|---|
| **USER** (손님) | 일반 사용자 | 웨이팅 등록·취소·미루기, 실시간 순번 확인, 리뷰 작성, 즐겨찾기 |
| **OWNER** (사장님) | 식당 점주 | 식당 등록·관리, 대기열 관리(호출/착석/노쇼), 웨이팅 접수 on/off |
| **ADMIN** (관리자) | 운영자 | 관리자 전용 기능 |

### 핵심 가치
- **정확성**: 분산락으로 동시 등록 시에도 순번이 꼬이지 않음
- **실시간성**: SSE로 웹 실시간 갱신, FCM으로 앱 푸시 알림
- **데이터 기반**: 요일·시간별 혼잡도 통계, AI 리뷰 요약으로 의사결정 지원

---

## 2. 전체 기술 스택과 선택 이유

### 2.1 코어 프레임워크

| 기술 | 버전 | 왜 사용했는가 |
|---|---|---|
| **Spring Boot** | 4.0.1 | 표준 웹 백엔드 프레임워크. 의존성 자동 구성, 내장 톰캣, 방대한 생태계 |
| **Java** | 17 (LTS) | record, switch 표현식, 텍스트 블록 등 최신 문법 + 장기 지원 |
| **Spring Data JPA** | (boot 관리) | 엔티티 중심 도메인 모델링, 더티 체킹 기반 영속성, 보일러플레이트 최소화 |
| **Gradle** | 9.x | 빌드 도구. Groovy DSL, 증분 빌드 |

### 2.2 데이터 저장소

| 기술 | 용도 | 왜 사용했는가 |
|---|---|---|
| **PostgreSQL** | 메인 RDB | 트랜잭션 신뢰성 + 강력한 확장 기능(PostGIS, JSONB) |
| **PostGIS** | 위치 기반 검색 | "내 주변 식당" 반경 검색을 DB 레벨에서 고속 처리 (`ST_DWithin`) |
| **JSONB** | 영업시간·메뉴·통계 저장 | 스키마가 유연한 반정형 데이터를 정규화 없이 한 컬럼에 저장하고 인덱싱/쿼리 가능 |
| **Redis** | 분산락 + 토큰 + 캐시 | 인메모리 고속 처리. 3가지 역할: ①Redisson 분산락 ②RefreshToken 저장 ③AI 요약 캐시 |

#### 왜 MySQL이 아니라 PostgreSQL인가?

SmartWaiting의 두 핵심 요구사항 — **위치 기반 반경 검색**과 **반정형 데이터(영업시간·메뉴·168칸 혼잡도 통계) 저장** — 이 DB 선택을 결정했습니다.

| 비교 항목 | PostgreSQL (채택) | MySQL |
|---|---|---|
| **공간 검색** | PostGIS가 사실상 표준. `ST_DWithin` + GiST 공간 인덱스로 반경 검색을 DB에서 고속 처리 | 공간 기능이 있으나 PostGIS 수준의 함수·인덱스 성숙도에 못 미침 |
| **JSON 처리** | `JSONB`(바이너리) — 인덱싱·`jsonb_path_exists` 같은 풍부한 경로 쿼리 지원 | `JSON` 타입은 있으나 인덱싱·연산자 지원이 제한적 |
| **표준 SQL·확장성** | 표준 SQL 준수도 높고 확장(extension) 생태계가 강력 | 상대적으로 제약 |

**핵심**: PostgreSQL **하나로** 관계형 + 공간(GIS) + 문서(JSON) 데이터를 모두 처리할 수 있어, 별도의 NoSQL·검색엔진을 도입하지 않고도 요구사항을 충족합니다. MySQL을 택했다면 위치 검색을 위한 별도 솔루션이나 애플리케이션 레벨 거리 계산이 필요해 복잡도가 올라갑니다. (실제 활용은 [06-store-statistics](./06-store-statistics.md) §7 참고)

### 2.3 인증·보안

| 기술 | 용도 | 왜 사용했는가 |
|---|---|---|
| **Spring Security** | 인증/인가 필터 체인 | URL·HTTP메서드·Role 기반 접근 제어의 표준 |
| **JWT (jjwt 0.11.5)** | Stateless 인증 | 서버 세션 없이 토큰만으로 인증 → 수평 확장 용이 |
| **OAuth2 Client** | 소셜 로그인 | Google·Kakao·Naver 간편 로그인 표준 프로토콜 |
| **BCrypt** | 비밀번호 해싱 | 단방향 + salt 내장, 무차별 대입 방어 |

### 2.4 동시성·실시간·알림

| 기술 | 용도 | 왜 사용했는가 |
|---|---|---|
| **Redisson** | 분산락 | 웨이팅 등록 Race Condition 방지. 가게 단위 락으로 순번 무결성 보장 |
| **SSE (SseEmitter)** | 서버→웹 단방향 실시간 | WebSocket보다 가벼우며 "서버가 클라에 알림만 보내는" 요구에 최적 |
| **FCM (Firebase Admin)** | 모바일 푸시 | 앱이 백그라운드여도 도달하는 푸시 알림 |
| **Spring Scheduler** | 배치 작업 | 리뷰 요청 알림, 유령 웨이팅 정리, 통계 집계의 주기 실행 |

### 2.5 AI·외부 인프라

| 기술 | 용도 | 왜 사용했는가 |
|---|---|---|
| **Spring AI + Google Gemini 2.5 Flash** | 리뷰 AI 요약 | 리뷰 50개+를 맛/분위기/서비스 3줄로 요약. Flash 모델로 비용·속도 균형 |
| **AWS S3 (SDK v2)** | 리뷰 이미지 저장 | 대용량 이미지 객체 스토리지. 서버 디스크 부담 제거 |
| **springdoc-openapi (Swagger)** | API 문서 자동화 | 어노테이션 기반 API 명세 자동 생성·테스트 UI |

---

## 3. 시스템 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                          클라이언트                                │
│         웹 (SSE 구독)          /          모바일 앱 (FCM)            │
└───────────────┬─────────────────────────────────┬────────────────┘
                │ REST API (JWT)                   │ SSE / Push
                ▼                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                        │
│                                                                   │
│  ┌──────────────┐   JWT Filter → SecurityFilterChain             │
│  │ Controller    │                                                │
│  └──────┬───────┘                                                 │
│         ▼                                                         │
│  ┌──────────────┐    ┌────────────────┐   ┌──────────────────┐   │
│  │ Facade(Lock)  │    │  Service        │   │  Scheduler        │  │
│  │ WaitingLock   │───▶│  비즈니스 로직   │   │  (배치 3종)       │   │
│  └──────────────┘    └───────┬────────┘   └──────────────────┘   │
│         │                    ▼                                    │
│         │            ┌────────────────┐                          │
│         │            │  Repository(JPA)│                          │
│         │            └───────┬────────┘                          │
└─────────┼────────────────────┼──────────────────────────────────┘
          │                    │
          ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌────────────────────┐
│      Redis        │  │   PostgreSQL      │  │   외부 서비스        │
│ ・Redisson 락     │  │  + PostGIS        │  │ ・Gemini (AI 요약)  │
│ ・RefreshToken    │  │  + JSONB          │  │ ・Firebase (FCM)    │
│ ・AI 요약 캐시    │  │                   │  │ ・AWS S3 (이미지)   │
└──────────────────┘  └──────────────────┘  └────────────────────┘
```

### 요청 처리 흐름 (예: 웨이팅 등록)
1. 클라이언트가 JWT를 담아 `POST /api/v1/waitings` 호출
2. `JwtAuthenticationFilter`가 토큰 검증 후 `SecurityContext`에 인증 주입
3. `WaitingController` → `WaitingLockFacade`가 **Redisson 분산락 획득**
4. 락 안에서 `WaitingService.registerWaiting()`가 검증·저장
5. 순번이 3번째면 `NotificationService`로 SSE 알림 발송
6. 락 해제 → 응답 반환

**흐름 설명** — 각 단계가 하는 일:

- **(1~2) 인증 주입**: 모든 요청은 먼저 보안 필터를 통과한다. 토큰이 유효해야만 인증 정보가 컨텍스트에 담겨 이후 단계에서 "누가 요청했는지"를 알 수 있다.
- **(3) 분산락 획득**: 등록은 동시에 몰릴 수 있어, Controller가 Service를 곧장 부르지 않고 Facade가 먼저 가게 단위 분산락을 잡는다.
- **(4) 직렬화 처리**: 락을 쥔 스레드만 검증·순번 계산·저장을 수행 → 같은 가게의 동시 등록이 한 명씩 처리되어 순번이 꼬이지 않는다.
- **(5~6) 알림·해제**: 저장 후 필요한 경우에만 알림을 보내고, 반드시 락을 해제한 뒤 응답을 돌려준다.

> "락 → 트랜잭션 → 알림 → 해제" 순서가 정확성의 핵심. 자세한 메커니즘은 [03-waiting](./03-waiting.md) §3.

---

## 4. 배포 & 인프라 개요

로컬에서는 `docker-compose`로 PostgreSQL·Redis·앱을 함께 띄우고, 운영에서는 **GitHub Actions로 자동 빌드·배포**하여 **AWS 3-tier 아키텍처** 위에서 구동합니다.

```
[배포] 개발자 git push → GitHub Actions(테스트·이미지 빌드) → ECR(이미지 저장) → EC2(자동 재배포)

[운영 트래픽]
  사용자 → Route53(DNS) → ALB(HTTPS 종단) → EC2(Spring Boot, Docker)
              → RDS(PostgreSQL+PostGIS) · ElastiCache(Redis)  [Private]
              → S3(이미지) · Gemini · FCM                     [외부]
```

- **CI/CD**: PR 시 테스트로 품질을 검증하고, main 병합 시 Docker 이미지를 빌드해 ECR에 올린 뒤 EC2가 새 이미지를 받아 무중단 교체합니다. 상세는 [07-cicd](./07-cicd.md).
- **AWS 인프라**: 외부에 노출되는 ALB·EC2는 Public, 데이터 계층(RDS·ElastiCache)은 Private에 격리해 앱 서버를 통해서만 접근하도록 설계합니다. 상세 설계와 전체 요청 경로는 [08-aws-infrastructure](./08-aws-infrastructure.md).

---

## 5. 빌드 & 실행

```bash
# 인프라 실행 (PostgreSQL + Redis)
docker-compose up -d db redis

# 테스트
./gradlew test

# 커버리지 리포트 (build/reports/jacoco/)
./gradlew test jacocoTestReport

# 앱 실행 (로컬 PostgreSQL 포트 5433)
./gradlew bootRun
```

### 필수 환경변수 (`.env`)
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_SECRET_KEY`, `GEMINI_API_KEY`,
`GOOGLE_CLIENT_ID/SECRET`, `KAKAO_CLIENT_ID/SECRET`, `NAVER_CLIENT_ID/SECRET`,
`AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `S3_BUCKET_NAME` (S3 활성화 시)

> 각 도메인별 상세 내용은 위 [문서 목차](#-문서-목차)를 참고하세요.
