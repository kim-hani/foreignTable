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

## 1-1. 전체 요청 흐름 (End-to-End) — 클라이언트 요청부터 응답까지

인프라 각 부품을 보기 전에, **사용자의 요청 하나가 위 그림을 어떻게 관통하는지** 먼저 따라가 보겠습니다. 시나리오는 손님의 웨이팅 등록입니다.

```
① 도메인 입력      사용자가 smartwaiting.com 접속
                        │
② DNS 조회        Route 53이 도메인 → ALB 주소 반환
                        │
③ HTTPS 연결      ACM 인증서로 TLS 암호화된 443 요청이 ALB에 도착
                        │
④ 트래픽 전달      ALB가 TLS를 풀고 EC2:8080(HTTP)으로 전달 (App SG가 ALB만 허용)
                        │
⑤ 인증            EC2의 Spring Boot: JwtAuthenticationFilter가 토큰 검증 → SecurityContext
                        │
⑥ 분산락          WaitingLockFacade → ElastiCache(Redis)에 가게 단위 락 요청 (Cache SG가 App만 허용)
                        │
⑦ 비즈니스·저장   락 안에서 WaitingService → RDS(PostgreSQL+PostGIS)에 검증·저장 (DB SG가 App만 허용)
                        │
⑧ 실시간 알림      순번 변동 시 SSE 발송 (ALB idle timeout 300s+ 로 장기 연결 유지)
                        │
⑨ 외부 푸시        1번째 변동 등 FCM 필요 시 EC2 → NAT Gateway → 인터넷(Firebase)
                        │
⑩ 응답            결과가 EC2 → ALB → 사용자로 반환, 락 해제
```

**흐름 설명**: 요청은 **공개 영역에서 점점 더 깊은 사설 영역으로** 들어갔다가 같은 길로 빠져나옵니다.

- **②~③ 진입**: 사용자가 도메인을 입력하면 Route 53이 이를 ALB 주소로 바꿔주고, 연결은 ACM 인증서로 암호화되어 ALB의 443 포트에 닿습니다. 여기까지가 "인터넷에서 보이는" 구간입니다.
- **④ 관문 통과**: ALB가 HTTPS를 종료(복호화)하고 내부 EC2의 8080으로 평문 전달합니다. 이때 App 보안그룹이 "ALB에서 온 트래픽만" 허용하므로, 외부에서 EC2로 직접 들어오는 길은 막혀 있습니다.
- **⑤~⑦ 처리**: EC2의 애플리케이션이 토큰으로 사용자를 확인하고, 동시성이 필요한 등록이므로 **Private에 있는 Redis(ElastiCache)에 락을 잡은 뒤** Private의 DB(RDS)에 저장합니다. 데이터 계층은 Public에서 보이지 않고 오직 앱 서버를 통해서만 접근됩니다(보안그룹 체이닝, §3).
- **⑧~⑨ 알림**: 순번이 바뀌면 웹에는 SSE로 즉시 보내고(ALB가 장기 연결을 끊지 않도록 idle timeout을 늘려둠), 앱 푸시가 필요하면 EC2가 NAT Gateway를 통해 외부 FCM으로 나갑니다. **들어오는 길(인터넷→ALB)과 나가는 길(EC2→NAT)이 분리**돼 있는 것이 포인트입니다.
- **⑩ 반환**: 응답은 들어온 길을 거슬러 ALB를 거쳐 사용자에게 돌아가고, 락은 그 전에 해제됩니다.

> 이 한 줄기 흐름에 등장한 모든 부품(VPC·서브넷·보안그룹·ALB·EC2·RDS·ElastiCache·NAT·S3)을 아래 §2부터 하나씩, **각 부품 안에서 무슨 일이 일어나는지** 설명합니다.

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

**내부에서 일어나는 일**: NAT Gateway는 Private 자원이 외부로 나갈 때 **출발지 IP를 자신의 공인 IP로 바꿔치기(주소 변환)**합니다. 그래서 응답은 NAT를 거쳐 다시 EC2로 돌아올 수 있지만, **외부가 먼저 EC2에 연결을 시도하는 것은 불가능**합니다(나가는 연결에 대한 응답만 통과). EC2가 ECR에서 이미지를 받거나 Gemini·FCM API를 호출하는 트래픽이 모두 이 길을 탑니다. 단, S3처럼 자주·대량으로 접근하는 AWS 서비스는 NAT 대신 VPC Endpoint로 빼면 NAT 통과 비용을 아낄 수 있습니다(§7).

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

**내부에서 일어나는 일**: 보안 그룹은 각 리소스의 네트워크 카드 앞에 붙는 **상태 기반(stateful) 방화벽**입니다. "허용"한 인바운드에 대한 응답(아웃바운드)은 규칙을 따로 열지 않아도 자동으로 통과합니다. 핵심은 인바운드 출발지를 **IP가 아니라 다른 보안 그룹으로 지정**한다는 점입니다 — 예컨대 DB SG는 "App SG를 단 자원에서 온 5432만 허용"이라고 적습니다. 그러면 앱 서버를 늘리거나 IP가 바뀌어도 규칙을 고칠 필요가 없고, App SG에 속하지 않은 그 무엇도(다른 서버·외부 IP) DB에 닿을 수 없습니다. 이 참조가 겹겹이 이어져 "인터넷→ALB→App→DB/Cache"라는 한 방향 통로만 남습니다.

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

**내부에서 일어나는 일**: ECR은 이미지를 **레이어 단위로 저장**해, 바뀐 레이어만 새로 올리고 받습니다(빌드·배포가 빨라짐). 인증은 토큰 기반인데, GitHub Actions는 AWS 자격증명으로, EC2는 IAM Role로 각각 임시 토큰을 발급받아 push/pull합니다 — 즉 **장기 비밀번호를 서버에 두지 않습니다**. 같은 이미지에 `latest`와 커밋 SHA 두 태그가 붙으므로, 평소엔 latest를 받고 문제가 생기면 특정 SHA 태그로 되돌릴 수 있습니다.

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

**내부에서 일어나는 일**: EC2는 **상태를 갖지 않는(stateless) 실행 환경**입니다. 부팅 시 IAM Role로 ECR에 인증해(별도 키 파일 없이 임시 자격증명을 자동 발급받음) 최신 이미지를 `docker pull`하고, 컨테이너를 띄우면 그 안의 Spring Boot가 RDS·ElastiCache 엔드포인트로 연결을 맺습니다. 데이터는 전부 RDS·ElastiCache·S3에 있으므로, **EC2 자체는 언제든 교체·증설해도 무방**합니다 — 이 무상태성이 ALB를 통한 다중화(EC2를 여러 대로 늘려 부하 분산)를 가능하게 합니다. 배포는 ECR에서 새 이미지를 받아 컨테이너만 갈아끼우는 방식이라, 서버를 재설치할 일이 없습니다.

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

**내부에서 일어나는 일**: 운영에서 DB·Redis를 EC2 밖으로 빼는 이유는 **생명주기를 분리**하기 위함입니다. RDS는 자동 백업·스냅샷·장애 시 대기 인스턴스로의 자동 전환(Multi-AZ)·마이너 버전 패치를 AWS가 대신 처리하고, ElastiCache도 노드 관리·복제를 맡아줍니다. 덕분에 앱 서버(EC2)를 재배포·교체해도 데이터는 영향받지 않습니다. 한 가지 운영 필수 작업은 RDS 생성 직후 **`CREATE EXTENSION postgis;`로 PostGIS를 켜는 것**입니다 — 이게 없으면 반경 검색(`ST_DWithin`) 쿼리가 실패합니다. ElastiCache는 분산락·RefreshToken·AI 캐시 세 역할을 모두 담당하므로, 이 노드가 단일 장애점이 되지 않도록 복제 구성을 권장합니다.

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

**내부에서 일어나는 일**: ALB는 **HTTPS 종단점**이자 **트래픽 분배기**입니다. 사용자와는 ACM 인증서로 암호화된 443 연결을 맺어 여기서 복호화(TLS termination)하고, 뒷단 EC2에는 가벼운 평문 8080으로 넘깁니다 — 덕분에 EC2는 인증서 관리 부담 없이 비즈니스 로직에만 집중합니다. 동시에 ALB는 등록된 EC2들에 **헬스 체크**를 보내 정상인 인스턴스에만 트래픽을 분배하고, 비정상 인스턴스는 자동으로 제외합니다. 그래서 EC2를 여러 대로 늘리면 별도 작업 없이 부하가 분산됩니다. 단 SSE는 한 연결을 길게 유지하는 특성이 있어, ALB가 "유휴 연결"로 오인해 끊지 않도록 idle timeout을 늘려야 합니다.

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

**내부에서 일어나는 일**: 이미지 같은 정적 파일을 DB나 서버 디스크에 두지 않고 S3에 두는 이유는 **용량 무제한·고내구성**과 **앱 서버 무상태화** 때문입니다. 앱은 업로드 시 형식·크기를 검증한 뒤 AWS SDK로 S3에 `putObject`만 하고, 저장된 객체의 URL을 DB(`Review.imageUrls`)에 기록합니다. 조회 시에는 그 URL로 브라우저가 S3에서 직접 이미지를 받으므로 앱 서버는 이미지 트래픽을 거치지 않습니다. 쓰기는 앱(IAM 권한)만, 읽기는 공개로 두는 정책이 일반적입니다.

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

**내부에서 일어나는 일**: 세 컴포넌트는 **"노출 → 수집 → 시각화"** 역할을 나눠 맡습니다. Spring Actuator + Micrometer가 JVM·HTTP·커스텀 지표를 `/actuator/prometheus` 한 경로에 텍스트로 노출하면, Prometheus가 이를 **주기적으로 끌어와(pull)** 시계열 DB에 쌓습니다(앱이 밀어 보내는 게 아니라 Prometheus가 긁어가는 방식). Grafana는 그 시계열을 질의해 대시보드로 그리고, 값이 임계치를 넘으면 Slack·이메일로 알립니다. SmartWaiting에서는 특히 **분산락 대기 시간**(락 경합으로 등록이 지연되는지)과 **Redis 캐시 히트율**(AI 요약 캐시 효율)이 서비스 특성상 핵심 관찰 지표입니다.

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

> 이 문서 맨 앞 **[§1-1 전체 요청 흐름](#1-1-전체-요청-흐름-end-to-end--클라이언트-요청부터-응답까지)**에서 클라이언트 요청부터 응답까지의 전 구간을 상세히 다뤘습니다. 요약하면 다음과 같습니다.

```
사용자 → Route53(DNS) → ACM/ALB(HTTPS 종료) → EC2(인증·비즈니스)
       → ElastiCache(분산락) → RDS(저장) → SSE/FCM(알림) → 응답
```

각 구간에서 어떤 보안그룹이 트래픽을 걸러내고 무슨 일이 일어나는지는 §1-1의 흐름 설명을 참고하세요.

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
