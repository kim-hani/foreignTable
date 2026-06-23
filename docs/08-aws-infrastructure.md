# 08. AWS 인프라 설계

> VPC·서브넷부터 EC2·ECR·ALB·Route53·RDS·ElastiCache·S3·모니터링까지, SmartWaiting에 최적화된 클라우드 아키텍처 설계와 트래픽 흐름

> 이 문서는 인프라 초심자도 이해할 수 있도록 **"왜 이렇게 설계하는가"**를 중심으로 작성했습니다.

---

## 1. 전체 아키텍처 한눈에 보기

```
                              인터넷 (사용자)
                                   │
                          ┌────────▼────────┐
                          │    Route 53      │  도메인 → IP 변환
                          │ smartwaiting.com │
                          └────────┬────────┘
                                   │
                          ┌────────▼────────┐
                          │       ACM        │  HTTPS 인증서
                          └────────┬────────┘
                                   │ 443(HTTPS)
┌──────────────────────────────────────────────────────────────────────┐
│  VPC (10.0.0.0/16)                                                      │
│                                                                        │
│   ┌─────────── Public Subnet (AZ-a, AZ-c) ───────────┐                 │
│   │                                                    │                │
│   │   ┌──────────────┐         ┌──────────────┐       │                │
│   │   │     ALB       │────────▶│  NAT Gateway  │       │                │
│   │   │ (로드밸런서)   │         │ (사설→외부)    │       │                │
│   │   └──────┬───────┘         └──────────────┘       │                │
│   │          │ 8080                                     │                │
│   │   ┌──────▼───────┐                                  │                │
│   │   │  EC2 (App)    │  Docker로 Spring Boot 실행        │                │
│   │   │  smart-waiting│                                  │                │
│   │   └──────┬───────┘                                  │                │
│   └──────────┼───────────────────────────────────────┘                │
│              │ (사설 통신)                                                │
│   ┌──────────▼─────── Private Subnet (AZ-a, AZ-c) ─────┐                │
│   │                                                     │               │
│   │   ┌──────────────┐    ┌──────────────┐             │               │
│   │   │  RDS          │    │  ElastiCache  │             │               │
│   │   │ (PostgreSQL   │    │  (Redis)      │             │               │
│   │   │  + PostGIS)   │    │               │             │               │
│   │   └──────────────┘    └──────────────┘             │               │
│   └─────────────────────────────────────────────────────┘               │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
        │                          │                         │
        ▼                          ▼                         ▼
  ┌──────────┐            ┌──────────────┐          ┌──────────────┐
  │   ECR     │            │      S3       │          │ Gemini/FCM   │
  │ (이미지)   │            │ (리뷰 이미지)  │          │ (외부 API)    │
  └──────────┘            └──────────────┘          └──────────────┘
```

---

## 2. VPC와 서브넷 — 인프라의 토대

### 2.1 VPC란?
**VPC(Virtual Private Cloud)** = AWS 안에 만드는 나만의 격리된 가상 네트워크. SmartWaiting의 모든 리소스(EC2·RDS·Redis)가 이 안에 들어갑니다.

```
VPC CIDR: 10.0.0.0/16   → 약 6.5만 개의 사설 IP 사용 가능 (10.0.0.0 ~ 10.0.255.255)
```

### 2.2 Public Subnet vs Private Subnet — 핵심 개념

이 구분이 인프라 보안의 핵심입니다.

| 구분 | Public Subnet | Private Subnet |
|---|---|---|
| **외부 접근** | 인터넷에서 **직접 접근 가능** | 인터넷에서 **직접 접근 불가** |
| **인터넷 연결** | Internet Gateway 통해 양방향 | NAT Gateway 통해 **나가기만** 가능 |
| **들어가는 것** | ALB, NAT Gateway | RDS, ElastiCache |
| **비유** | 건물 1층 로비 (외부인 출입) | 건물 금고실 (직원만, 외부 직접 출입 불가) |

```
왜 나누는가?
  DB·Redis를 Public에 두면 → 인터넷에서 직접 공격 가능 (해킹 위험)
  Private에 격리하면 → 오직 앱 서버(EC2)를 통해서만 접근 → 안전
```

### 2.3 서브넷 배치 설계 (가용 영역 분산)

**AZ(Availability Zone, 가용 영역)** = 물리적으로 분리된 데이터센터. 2개 AZ에 분산 배치해 한 곳이 죽어도 서비스가 유지됩니다.

| 서브넷 | CIDR(예시) | AZ | 들어가는 리소스 |
|---|---|---|---|
| Public Subnet 1 | 10.0.1.0/24 | ap-northeast-2a | ALB, NAT Gateway |
| Public Subnet 2 | 10.0.2.0/24 | ap-northeast-2c | ALB (이중화) |
| Private Subnet 1 | 10.0.11.0/24 | ap-northeast-2a | EC2, RDS, ElastiCache |
| Private Subnet 2 | 10.0.12.0/24 | ap-northeast-2c | RDS(대기), ElastiCache(대기) |

> 💡 **EC2 배치 선택지**: 단순화하려면 EC2를 Public Subnet에 두고 ALB만 거쳐 접근하게 할 수도 있습니다. 더 안전하게 하려면 EC2도 Private에 두고 ALB만 Public에 노출합니다. SmartWaiting 규모에서는 **EC2를 Private에 두고 ALB를 통해서만 접근**하는 방식을 권장합니다.

### 2.4 NAT Gateway — Private의 외부 통신 통로

Private Subnet의 EC2는 인터넷에서 **들어올 수는 없지만**, **나가야 할 일**은 있습니다 (ECR에서 이미지 pull, Gemini/FCM API 호출).

```
EC2(Private) ──▶ NAT Gateway(Public) ──▶ Internet Gateway ──▶ 인터넷
              나가는 요청만 허용 (외부에서 먼저 들어오는 연결은 차단)
```

---

## 3. 보안 그룹 — 가상 방화벽

**보안 그룹(Security Group)** = 각 리소스 앞에 붙는 방화벽. "누가 어느 포트로 들어올 수 있는가"를 정의합니다.

### 3.1 계층별 보안 그룹 전략 (체이닝)

```
인터넷 ──80/443──▶ [ALB SG] ──8080──▶ [App SG] ──┬─5432─▶ [DB SG]
                                                  └─6379─▶ [Cache SG]
```

| 보안 그룹 | 인바운드 허용 | 의미 |
|---|---|---|
| **ALB SG** | 인터넷(0.0.0.0/0) → 80, 443 | 누구나 웹 접속 가능 |
| **App SG** | **ALB SG** → 8080 | ALB를 통해서만 앱 접근 (직접 접근 차단) |
| **DB SG** | **App SG** → 5432 | 앱 서버만 DB 접근 |
| **Cache SG** | **App SG** → 6379 | 앱 서버만 Redis 접근 |

### 3.2 핵심: IP가 아닌 "보안 그룹 참조"

```
DB SG 인바운드 = "App SG에서 오는 5432 트래픽만 허용"
  → 앱 서버 IP가 바뀌어도 규칙 수정 불필요
  → DB는 오직 앱을 통해서만 접근 가능 (외부·다른 서버 완전 차단)
```

이렇게 하면 **계단식 접근 제어**가 완성됩니다: 인터넷 → ALB → 앱 → DB/Redis 순으로만 통과 가능.

---

## 4. 컴퓨팅 & 배포 — EC2 + ECR

### 4.1 ECR (Elastic Container Registry) — Docker 이미지 저장소

CI/CD가 만든 Docker 이미지를 보관하는 **AWS의 비공개 이미지 창고**.

```
GitHub Actions ──docker push──▶ ECR (smart-waiting-app 리포지토리)
                                  │
EC2 ──docker pull──────────────◀─┘  (IAM Role로 인증)
```

| 항목 | 설명 |
|---|---|
| 리포지토리명 | `smart-waiting-app` |
| 이미지 태그 | `latest` + 커밋 SHA ([07-cicd](./07-cicd.md) §6) |
| 인증 | EC2의 IAM Role에 ECR pull 권한 부여 (키 불필요) |

### 4.2 EC2 — 애플리케이션 서버

Spring Boot 앱을 Docker 컨테이너로 실행하는 가상 서버.

| 항목 | 권장 |
|---|---|
| 인스턴스 타입 | `t3.small` 이상 (Spring Boot 최소 1GB RAM) |
| 배치 | Private Subnet |
| 설치 | Docker + Docker Compose |
| IAM Role | ECR pull 권한 (+ S3, Secrets Manager 접근 시 추가) |
| 환경변수 | GitHub Secrets → 배포 시 주입 (또는 Secrets Manager) |

### 4.3 배포 흐름 (CD와 연결)

```
[07-cicd]의 CD 파이프라인
  GitHub Actions: docker build → ECR push
        │
        ▼ SSH
  EC2: docker pull (ECR) → docker compose up -d
        │
        ▼
  앱 컨테이너가 RDS·ElastiCache에 사설 네트워크로 연결
```

> EC2의 앱은 `docker-compose.yml`에서 DB·Redis 접속 정보를 **RDS·ElastiCache 엔드포인트**로 설정 (로컬 compose의 `db`/`redis` 컨테이너 대신).

---

## 5. 데이터 계층 — RDS + ElastiCache

로컬에서는 Docker로 띄우던 DB·Redis를, 운영에서는 **AWS 관리형 서비스**로 분리합니다.

### 5.1 왜 관리형으로 분리하는가?

```
EC2 안에 DB 컨테이너 ❌              RDS (관리형) ✅
EC2 죽으면 DB도 죽음                  EC2와 독립적으로 생존
백업·패치 수동                        자동 백업·자동 패치
스케일링 어려움                       클릭으로 확장
```

### 5.2 RDS (PostgreSQL + PostGIS)

| 항목 | 설정 |
|---|---|
| 엔진 | PostgreSQL 15 |
| 인스턴스 | `db.t3.micro` (시작), 트래픽 증가 시 확장 |
| 배치 | Private Subnet (AZ 분산, Multi-AZ 권장) |
| 필수 확장 | `CREATE EXTENSION postgis;` ← **위치 검색 기능에 필수** |
| 보안 | App SG에서만 5432 인바운드 |
| 연결 | `application.yml`의 `spring.datasource.url` → RDS 엔드포인트 |

> SmartWaiting은 PostGIS(반경 검색) + JSONB(영업시간·메뉴·통계)를 쓰므로, RDS도 **PostGIS 확장 활성화**가 반드시 필요합니다. ([06-store-statistics](./06-store-statistics.md))

### 5.3 ElastiCache (Redis)

| 항목 | 설정 |
|---|---|
| 엔진 | Redis 7.x |
| 인스턴스 | `cache.t3.micro` |
| 배치 | Private Subnet |
| 보안 | App SG에서만 6379 인바운드 |
| 용도 | ①Redisson 분산락 ②RefreshToken ③AI 요약 캐시 ([03](./03-waiting.md)·[02](./02-auth-security.md)·[05](./05-review-ai.md)) |

> ⚠️ **분산락 주의**: SmartWaiting의 웨이팅 등록은 Redisson 분산락에 의존하므로, ElastiCache 연결이 끊기면 등록이 막힙니다. 안정성을 위해 Redis도 이중화(Replication) 고려.

---

## 6. 네트워크 진입점 — ALB + Route53 + ACM

### 6.1 ALB (Application Load Balancer)

사용자 요청을 받아 EC2로 전달하는 **트래픽 분배기 겸 HTTPS 종단점**.

```
인터넷 ──443(HTTPS)──▶ ALB ──8080(HTTP)──▶ EC2(App)
         80(HTTP) ──▶ ALB → 443으로 리다이렉트 (강제 HTTPS)
```

| 역할 | 설명 |
|---|---|
| HTTPS 종료 | ACM 인증서로 SSL 처리, 뒤단 EC2는 평문 8080 |
| 부하 분산 | EC2를 여러 대로 늘리면 자동 분배 |
| 헬스 체크 | 비정상 EC2를 자동 제외 |
| 보안 | EC2는 ALB SG에서만 접근 허용 → 직접 노출 차단 |

> ⚠️ **SSE 주의사항**: SmartWaiting은 SSE(`/api/v1/notifications/subscribe`)로 장시간 연결을 유지합니다. ALB의 **idle timeout을 300초 이상**으로 늘려야 알림 연결이 중간에 끊기지 않습니다. ([04-notification](./04-notification.md))

### 6.2 ACM (Certificate Manager)
무료 SSL/TLS 인증서를 발급·자동 갱신. ALB에 연결해 HTTPS를 제공합니다.

### 6.3 Route 53 — DNS

도메인 이름(`smartwaiting.com`)을 ALB 주소로 연결합니다.

```
사용자가 smartwaiting.com 입력
  │
  ▼
Route 53: 도메인 → ALB DNS (A 레코드 Alias)
  │
  ▼
ALB로 요청 전달
```

| 레코드 | 설정 |
|---|---|
| A (Alias) | `smartwaiting.com` → ALB |
| CNAME | `www.smartwaiting.com` → `smartwaiting.com` |

---

## 7. 스토리지 — S3 (리뷰 이미지)

리뷰 이미지를 저장하는 **객체 스토리지**. ([05-review-ai](./05-review-ai.md) §6)

```
사용자 ──이미지 업로드──▶ EC2(App)
                          │ AWS SDK
                          ▼
                        S3 버킷 (reviews/{uuid}.jpg)
                          │
조회 시 ◀──이미지 URL─────┘
https://{bucket}.s3.ap-northeast-2.amazonaws.com/reviews/...
```

| 항목 | 설정 |
|---|---|
| 리전 | `ap-northeast-2` (서울) |
| 경로 | `reviews/{uuid}.{ext}` |
| 권한 | EC2 IAM Role에 S3 put/get 권한 (또는 IAM 키) |
| 정책 | 이미지 읽기는 공개, 쓰기는 앱만 |

> S3는 VPC 밖의 AWS 서비스. EC2(Private)에서 접근 시 NAT Gateway를 거치거나 **VPC Endpoint(S3 Gateway Endpoint)**를 두면 NAT 비용 없이 직접 연결 가능.

---

## 8. 모니터링 — Prometheus + Grafana

서비스 상태를 실시간으로 관찰하는 모니터링 스택.

```
Spring Boot App ──/actuator/prometheus──▶ Prometheus ──▶ Grafana (대시보드)
  (메트릭 노출)        (메트릭 수집·저장)      (시각화·알림)
```

### 8.1 구성

| 컴포넌트 | 역할 |
|---|---|
| **Spring Actuator + Micrometer** | 앱이 메트릭을 `/actuator/prometheus`로 노출 |
| **Prometheus** | 주기적으로 메트릭 수집·저장 (시계열 DB) |
| **Grafana** | 대시보드 시각화 + 임계치 알림(Slack/이메일) |

### 8.2 관찰할 핵심 메트릭

SmartWaiting 특성상 아래 지표가 중요합니다.

| 메트릭 | 왜 중요한가 |
|---|---|
| 웨이팅 등록 TPS | 피크 시간대 부하 파악 |
| **분산락 대기 시간** | Redisson 락 경합 → 등록 지연 감지 ([03](./03-waiting.md)) |
| Redis 캐시 히트율 | AI 요약 캐시 효율 ([05](./05-review-ai.md)) |
| API P99 응답시간 | 사용자 체감 성능 |
| SSE 활성 연결 수 | 실시간 알림 부하 ([04](./04-notification.md)) |

> 모니터링 서버는 별도 EC2 또는 앱 서버에 Docker Compose로 구성. Prometheus가 `http://앱서버:8080/actuator/prometheus`를 스크래핑.

---

## 9. 시크릿 관리 — AWS Secrets Manager (권장)

DB 비밀번호·JWT 시크릿·OAuth 키·Firebase JSON 등 모든 비밀값을 한 곳에서 중앙 관리.

```
현재: .env 파일 + docker-compose 환경변수
권장: AWS Secrets Manager
       │
       ▼ EC2 IAM Role (secretsmanager:GetSecretValue)
   앱이 부팅 시 자동으로 시크릿 주입 → .env 파일을 서버에 둘 필요 없음
```

| 장점 | 설명 |
|---|---|
| 키 노출 위험 제거 | 서버·이미지·깃에 비밀값 없음 |
| 중앙 관리·자동 교체 | 한 곳에서 회전(rotation) |
| Firebase JSON 보관 | FCM 서비스 계정 JSON도 여기에 ([04](./04-notification.md)) |

---

## 10. 전체 요청 흐름 (End-to-End)

**시나리오**: 사용자가 웨이팅을 등록하는 전체 경로

```
① 사용자 → smartwaiting.com (Route 53가 ALB IP 반환)
② HTTPS 요청 → ACM 인증서로 암호화 → ALB
③ ALB → EC2(App):8080 으로 전달 (App SG가 ALB만 허용)
④ JWT 인증 필터 통과 → WaitingController
⑤ WaitingLockFacade → ElastiCache(Redis)에 분산락 요청 (Cache SG가 App만 허용)
⑥ 락 안에서 WaitingService → RDS(PostgreSQL)에 저장 (DB SG가 App만 허용)
⑦ 순번 3번째면 → NotificationService SSE 알림 (ALB idle timeout 300s+)
⑧ 1번째 변동 시 → FCM(외부) 푸시 (EC2 → NAT Gateway → 인터넷)
⑨ 응답 → ALB → 사용자
```

---

## 11. 구축 순서 (의존성 기반)

인프라는 **의존 관계 순서대로** 구축합니다.

```
① VPC + Subnet + IGW + NAT          ← 모든 것의 토대
        │
② 보안 그룹 (ALB/App/DB/Cache SG)
        │
   ┌────┼────────────┬──────────────┐
③ RDS  ④ ElastiCache  ⑤ S3 + IAM     ⑥ ECR
        │              (리뷰 이미지)    (이미지 저장소)
        ▼
⑦ EC2 (Docker 설치, IAM Role)
        │
⑧ ALB + ACM (HTTPS)
        │
⑨ Route 53 (도메인 연결)
        │
⑩ GitHub Actions CD 연결 ([07-cicd])
        │
⑪ Prometheus + Grafana (모니터링)
        │
⑫ Secrets Manager (시크릿 중앙화) — 선택
```

| 단계 | 선행 조건 | 관련 기능 |
|---|---|---|
| ① VPC | 없음 (최우선) | 전체 기반 |
| ⑤ S3 + IAM | VPC | 리뷰 이미지 활성화 ([05](./05-review-ai.md)) |
| ⑥ ECR | 없음 | CI/CD 선행 ([07](./07-cicd.md)) |
| ⑧ ALB | EC2 | SSE idle timeout 주의 ([04](./04-notification.md)) |
| ⑪ 모니터링 | Actuator 연동 | 분산락·캐시 관찰 |

---

## 12. 비용·확장 고려사항

| 관점 | 권장 |
|---|---|
| **초기(MVP)** | t3.micro/small, 단일 AZ로 시작 → 비용 최소화 |
| **확장 시** | Multi-AZ(RDS/Redis), EC2 다중화 + ALB 자동 분배 |
| **NAT 비용** | S3는 VPC Endpoint로 NAT 우회, 트래픽 비용 절감 |
| **모니터링** | 작은 규모면 CloudWatch로 시작, 커지면 Prometheus+Grafana |

> 이 설계는 SmartWaiting의 핵심 의존성(PostGIS·Redis 분산락·SSE·S3·FCM)을 모두 고려한 **확장 가능한 표준 3-tier 아키텍처**입니다. 트래픽이 늘면 EC2 다중화와 [향후 과제]인 Redis Pub/Sub 기반 SSE 확장([04](./04-notification.md) §2.3)으로 수평 확장할 수 있습니다.
