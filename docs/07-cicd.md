# 07. CI/CD & Docker

> GitHub Actions 자동 빌드·배포 파이프라인, Docker 멀티스테이지 빌드, Docker Compose 로컬/운영 구성

---

## 1. CI/CD 한눈에 보기

**목표**: 개발자가 코드를 push하면 → 자동으로 테스트 → Docker 이미지 빌드 → 레지스트리(ECR) 푸시 → 서버(EC2) 자동 배포.

```
개발자
  │  git push origin main
  ▼
┌─────────────────────────────────────────────────────────┐
│                   GitHub Actions                          │
│                                                           │
│  ① CI (Pull Request 시)                                   │
│     체크아웃 → JDK 설정 → ./gradlew test → 커버리지 검증    │
│                                                           │
│  ② CD (main push 시)                                      │
│     Docker 이미지 빌드 → ECR 로그인 → 이미지 푸시           │
│                              │                            │
│                              ▼ SSH 접속                    │
└──────────────────────────────┼───────────────────────────┘
                               ▼
                        ┌──────────────┐
                        │  EC2 서버      │
                        │  docker pull  │  ← ECR에서 새 이미지 받기
                        │  docker compose up -d │  ← 무중단 재시작
                        └──────────────┘
```

- **CI (Continuous Integration)**: 코드 통합 시점마다 자동 검증 (테스트·빌드)
- **CD (Continuous Deployment)**: 검증 통과한 코드를 자동으로 서버에 배포

**흐름 설명** — 개발자의 행동은 `git push` 하나뿐이고, 그 뒤는 두 갈래로 자동화된다:

- **PR을 올리면 → CI**: 테스트로 코드 품질을 검증한다 (깨진 코드는 병합을 막음).
- **main에 병합되면 → CD**: Docker 이미지를 만들어 ECR에 올린 뒤, EC2가 그 이미지를 받아 스스로 재배포한다.

> "검증은 병합 전에, 배포는 병합 후에" 자동으로 일어나, 사람이 SSH로 수동 빌드·복사하던 과정이 사라진다.

---

## 2. Docker — 애플리케이션 컨테이너화

### 2.1 Docker를 쓰는 이유

| 문제 | Docker의 해결 |
|---|---|
| "내 PC에선 됐는데 서버에선 안 돼요" | OS·Java·라이브러리를 **이미지에 통째로 패키징** → 어디서나 동일 실행 |
| 서버마다 환경 세팅 반복 | 이미지 하나만 받으면 끝 (`docker pull` → `docker run`) |
| 배포 롤백 어려움 | 이전 이미지 태그로 즉시 되돌리기 |

### 2.2 Dockerfile — 멀티스테이지 빌드

현재 프로젝트의 `Dockerfile`은 **2단계(멀티스테이지) 빌드**로 최종 이미지를 가볍게 만듭니다.

```dockerfile
# ── Stage 1: 빌드 전용 (JDK + Gradle로 JAR 생성) ──
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar -x test   # JAR 빌드 (테스트는 CI에서 별도 수행)

# ── Stage 2: 실행 전용 (빌드 결과 JAR만 복사) ──
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone                # 한국 시간대 설정
COPY --from=builder /app/build/libs/*.jar app.jar    # Stage 1의 산출물만 가져옴
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 왜 멀티스테이지인가?
```
단일 스테이지 ❌                  멀티스테이지 ✅
JDK + Gradle + 소스 + JAR 전부    Stage1(빌드) 결과물 JAR만 Stage2로 복사
→ 이미지 무겁고 소스 노출          → 최종 이미지엔 JRE + JAR만 → 가볍고 안전
```

**흐름 설명** — 빌드는 두 방으로 나뉜다 ("작업실은 버리고 완성품만 출고"):

- **Stage 1 (작업실)**: JDK·Gradle·소스 전체가 들어찬 환경에서 `bootJar`로 실행 가능한 JAR을 만든다.
- **Stage 2 (출고)**: 깨끗한 새 이미지로 시작해, Stage 1에서 만든 **JAR 하나만** `COPY --from=builder`로 가져온다.
- **결과**: 최종 이미지에는 빌드 도구·소스 코드가 남지 않고 실행에 필요한 것만 남아 → 크기가 작고 소스 노출 위험도 없다.

| 설계 포인트 | 설명 |
|---|---|
| `alpine` 베이스 | 경량 리눅스 → 이미지 크기 최소화 |
| `bootJar -x test` | 이미지 빌드 시 테스트 제외 (테스트는 CI 단계가 담당, 빌드 속도↑) |
| 시간대 설정(`Asia/Seoul`) | 스케줄러(`@Scheduled`)·로그 시각 정확성 (리뷰 알림, 새벽 배치) |
| `ENTRYPOINT` | 컨테이너 시작 = Spring Boot 실행 |

> ⚠️ **개선 여지**: 실행 단계도 `jdk` 대신 `jre`(또는 distroless)를 쓰면 더 가벼워짐. FCM 서비스 계정 JSON은 이미지에 넣지 말고 런타임 마운트/시크릿으로 주입 권장.

---

## 3. Docker Compose — 여러 컨테이너 오케스트레이션

### 3.1 Compose를 쓰는 이유
앱은 혼자 못 돕니다 — **PostgreSQL + Redis + 앱** 3개 컨테이너가 함께 떠야 합니다. `docker-compose.yml` 한 파일로 셋을 **한 번에 정의·실행·연결**합니다.

```bash
docker-compose up -d db redis     # 인프라만 (로컬 개발: 앱은 IDE에서 실행)
docker-compose up -d              # 전체 (운영: 앱까지 컨테이너로)
```

### 3.2 현재 docker-compose.yml 구조

```yaml
services:
  # ① PostgreSQL + PostGIS (공간 검색 지원 이미지)
  db:
    image: postgis/postgis:15-3.3-alpine
    environment: { POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB }
    ports: ["5432:5432"]
    volumes: ["./postgres_data:/var/lib/postgresql/data"]   # 데이터 영속화
    healthcheck: pg_isready ...                              # 준비 완료 확인

  # ② Redis (분산락·토큰·캐시)
  redis:
    image: redis:alpine
    ports: ["${REDIS_PORT}:6379"]
    healthcheck: redis-cli ping ...

  # ③ Spring Boot 앱
  app:
    build: .                          # 같은 폴더 Dockerfile로 빌드
    ports: ["8080:8080"]
    depends_on:                       # ★ DB·Redis가 healthy 된 뒤에 시작
      db:    { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME}  # 컨테이너명으로 통신
      SPRING_DATA_REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
      # ... OAuth, Gemini 키 등
```

### 3.3 핵심 메커니즘

| 메커니즘 | 설명 |
|---|---|
| **서비스 이름 = 호스트명** | 앱은 DB를 `db:5432`, Redis를 `redis:6379`로 접근. Compose가 내부 DNS 자동 구성 |
| **`depends_on` + `healthcheck`** | DB·Redis가 **완전히 준비된 후** 앱 시작 → 기동 시 연결 실패 방지 |
| **`volumes` (DB 영속화)** | 컨테이너를 지워도 `./postgres_data`에 데이터 보존 |
| **`${변수}` (`.env` 주입)** | 비밀번호·키를 코드가 아닌 `.env`에서 주입 → 깃에 비밀 노출 방지 |

### 3.4 환경별 구성 전략

```
[ 로컬 개발 ]                          [ 운영(EC2) ]
docker-compose up -d db redis          docker-compose up -d   (또는 단일 app 컨테이너)
앱은 IDE에서 bootRun (디버깅 편함)       DB/Redis는 관리형 서비스(RDS/ElastiCache)로 분리 권장
DB 포트 5433 등 로컬 매핑                앱 이미지는 ECR에서 pull
```

> 💡 **운영 권장**: EC2에서는 DB·Redis를 compose 컨테이너가 아닌 **RDS·ElastiCache(관리형)**로 분리하는 게 안정적. compose는 앱 컨테이너 실행에만 사용하고, DB 접속 정보를 RDS 엔드포인트로 교체. (자세한 내용 [08-aws-infrastructure](./08-aws-infrastructure.md))

---

## 4. GitHub Actions CI 파이프라인 (테스트 자동화)

PR을 올릴 때마다 자동으로 테스트를 돌려 **깨진 코드의 병합을 차단**합니다.

### 4.1 `.github/workflows/ci.yml` (설계안)

```yaml
name: CI

on:
  pull_request:
    branches: [ main ]          # main으로 향하는 PR마다 실행

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4                     # ① 코드 체크아웃
      - uses: actions/setup-java@v4                    # ② JDK 17 설정
        with:
          distribution: temurin
          java-version: '17'
      - name: Grant execute permission
        run: chmod +x ./gradlew
      - name: Run tests with coverage                  # ③ 테스트 + JaCoCo
        run: ./gradlew test jacocoTestReport
      - name: Upload coverage report                   # ④ 커버리지 리포트 보관
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: build/reports/jacoco/
```

### 4.2 CI 흐름

```
PR 생성/업데이트
  │
  ▼
GitHub Actions 러너(ubuntu) 시작
  │
  ① 코드 체크아웃
  ② JDK 17 세팅
  ③ ./gradlew test  ── 실패 시 ──▶ ❌ PR에 빨간 X, 병합 차단
  │  성공
  ④ JaCoCo 커버리지 리포트 생성
  ▼
✅ PR에 초록 체크 → 리뷰 후 병합 가능
```

**흐름 설명** — PR이 생성·갱신될 때마다 GitHub이 깨끗한 우분투 러너를 띄운다:

- **① 체크아웃**: 코드를 받는다.
- **② 환경 설정**: JDK 17을 세팅한다.
- **③ 테스트**: 테스트를 돌린다 → **하나라도 실패하면 PR에 빨간 X, 병합 차단**. (사람 손 없이 깨진 코드의 main 유입을 막는 것이 CI의 핵심)
- **④ 리포트**: 통과 시 커버리지 리포트를 산출물로 남긴다 → 초록 체크가 떠야 병합 가능.

> 테스트 시 DB·Redis가 필요하면 GitHub Actions `services:`로 PostgreSQL·Redis 컨테이너를 띄우거나, 슬라이스 테스트(`@DataJpaTest`)·목(Mockito) 기반으로 외부 의존 없이 검증.

---

## 5. GitHub Actions CD 파이프라인 (자동 배포)

main에 코드가 병합되면 자동으로 이미지를 만들고 서버에 배포합니다. (사용자 요구사항: *"코드를 올리면 자동으로 감지해서 docker 이미지를 만든다"*)

### 5.1 `.github/workflows/cd.yml` (설계안)

```yaml
name: CD

on:
  push:
    branches: [ main ]          # main에 push(병합)되면 실행

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # ① AWS 자격증명 설정 (GitHub Secrets 사용)
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-northeast-2

      # ② ECR 로그인
      - uses: aws-actions/amazon-ecr-login@v2
        id: login-ecr

      # ③ Docker 이미지 빌드 + 태그 + ECR 푸시
      - name: Build and push image
        env:
          REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          REPO: smart-waiting-app
          TAG: ${{ github.sha }}        # 커밋 해시를 태그로 → 버전 추적
        run: |
          docker build -t $REGISTRY/$REPO:$TAG -t $REGISTRY/$REPO:latest .
          docker push $REGISTRY/$REPO:$TAG
          docker push $REGISTRY/$REPO:latest

      # ④ EC2 SSH 접속 → 새 이미지로 재배포
      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            aws ecr get-login-password | docker login --username AWS --password-stdin $REGISTRY
            docker pull $REGISTRY/smart-waiting-app:latest
            docker compose up -d app        # 새 이미지로 무중단 교체
            docker image prune -f           # 오래된 이미지 정리
```

### 5.2 CD 전체 파이프라인

```
main 브랜치에 병합(push)
  │
  ▼ GitHub Actions 자동 트리거
① 코드 체크아웃
② AWS 자격증명 (Secrets)
③ ECR 로그인
④ docker build → 이미지 생성 (태그: 커밋 SHA + latest)
⑤ docker push → ECR에 이미지 업로드
  │
  ▼ SSH
⑥ EC2 접속
⑦ docker pull (ECR에서 새 이미지)
⑧ docker compose up -d (재시작)
⑨ 옛 이미지 정리
  │
  ▼
✅ 새 버전 라이브
```

**흐름 설명** — CD는 "이미지를 올리는 전반부"와 "서버가 받아 교체하는 후반부"로 나뉜다:

- **전반부 (①~⑤, 이미지 → ECR)**: 코드를 받고 AWS 자격증명(Secrets)으로 ECR에 로그인 → `docker build`로 이미지 생성 → **커밋 SHA + latest** 두 태그를 붙여 ECR에 푸시. (SHA 태그는 배포 버전 추적·롤백 지점용, §6)
- **후반부 (⑥~⑨, 서버 교체)**: GitHub Actions가 EC2에 SSH 접속 → `docker pull`로 새 이미지 수신 → `docker compose up -d`로 컨테이너 교체 → 옛 이미지 정리(`prune`).

> 이 전 과정이 main 병합 한 번으로 끝까지 자동 진행된다.

### 5.3 GitHub Secrets에 보관할 값

| Secret | 용도 |
|---|---|
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | ECR 푸시·배포 권한 |
| `EC2_HOST` / `EC2_USER` / `EC2_SSH_KEY` | 배포 대상 서버 SSH 접속 |
| `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` 등 | 런타임 환경변수 (또는 AWS Secrets Manager로 통합) |

> ⚠️ **비밀값은 절대 코드/이미지에 넣지 않음**. GitHub Secrets → 런타임 주입. 더 나아가 AWS Secrets Manager로 중앙 관리하면 키 노출 위험 최소화.

---

## 6. 이미지 태그 전략

| 태그 | 용도 |
|---|---|
| `latest` | 항상 최신 배포본을 가리킴 (EC2가 pull) |
| `{git-sha}` (예: `a1b2c3d`) | 특정 커밋 버전 고정 → **롤백 시 이 태그로 되돌림** |

```
롤백 시나리오:
  새 배포 후 장애 발생
  → 이전 정상 커밋 SHA 태그로 docker pull
  → docker compose up -d
  → 즉시 안정 버전 복구
```

---

## 7. CI/CD 도입 효과

| 항목 | 도입 전 | 도입 후 |
|---|---|---|
| 배포 | 수동 SSH·빌드·복사 | push 한 번으로 자동 |
| 품질 | 깨진 코드 병합 가능 | CI 테스트 통과해야 병합 |
| 일관성 | 환경마다 다른 빌드 | 동일 Docker 이미지 |
| 롤백 | 어려움 | 이전 태그로 즉시 |
| 추적성 | 누가 뭘 배포했는지 불명확 | 커밋 SHA = 이미지 버전 |

> 다음 단계: 이 파이프라인이 배포할 **AWS 인프라의 설계**는 [08-aws-infrastructure.md](./08-aws-infrastructure.md)에서 다룹니다.
