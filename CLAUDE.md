# CLAUDE.md — SmartWaiting Project Guide

## 1. 프로젝트 개요

**SmartWaiting**: 음식점 대기 줄 관리 플랫폼 (Spring Boot 4.0.1 / Java 17)

- 핵심 도메인: 웨이팅, 실시간 알림(SSE+FCM), AI 리뷰 요약
- 상세 개발 규칙: [`shrimp-rules.md`](./shrimp-rules.md) — 반드시 참조
- 커밋/PR 형식: [`.claude/git-conventions.md`](./.claude/git-conventions.md) — 반드시 참조
- 전체 PRD: [`prd.md`](./prd.md)

---

## 2. 개발 환경

### GitHub CLI (gh)

- **설치 방법**: Scoop (`C:\Users\ha_ni\scoop\shims` — 시스템 PATH에 등록됨)
- **버전**: gh 2.95.0
- **인증 계정**: `kim-hani` (GitHub, keyring 저장)
- **연결 저장소**: `kim-hani/foreignTable`
- **프로토콜**: HTTPS
- **토큰 스코프**: `gist`, `read:org`, `repo`, `workflow`

gh CLI를 사용해 이슈 생성, PR 생성, PR 조회 등 모든 GitHub 작업을 터미널에서 수행할 수 있다.

```bash
# 이슈 생성
gh issue create --title "제목" --body "내용" --label "enhancement"

# PR 생성
gh pr create --title "제목" --body "내용"

# 현재 브랜치 PR 조회
gh pr view

# 이슈 목록
gh issue list
```

### Git 브랜치 전략

- `main`: 배포 브랜치
- `feat/{기능명}`: 신규 기능 개발
- `fix/{버그명}`: 버그 수정
- `test/{테스트명}`: 테스트 추가/수정
- `chore/{작업명}`: 빌드/설정 변경

---

## 3. 빌드 & 실행

```bash
# 테스트 실행
./gradlew test

# 커버리지 리포트 (build/reports/jacoco/)
./gradlew test jacocoTestReport

# Docker Compose로 인프라 실행 (db + redis)
docker-compose up -d db redis

# 앱 실행 (로컬 PostgreSQL 포트: 5433)
./gradlew bootRun
```

---

## 4. 패키지 구조 요약

```
OneTwo.SmartWaiting
├── auth/               # 인증 (JWT, RefreshToken)
├── config/             # Spring 설정 (Security, Redis, Firebase, Swagger)
├── common/
│   ├── domain/         # BaseEntity (softDelete)
│   └── exception/      # BusinessException, ErrorCode(enum)
└── domain/
    ├── member/         # 회원 (USER / OWNER / ADMIN)
    ├── store/          # 식당 (PostGIS, JSONB)
    ├── waiting/        # 핵심 도메인 + WaitingLockFacade + CleanupScheduler
    ├── review/         # 리뷰 + AI 요약 (Gemini)
    ├── favorite/       # 즐겨찾기
    ├── notification/   # SSE + FCM
    └── oauth/          # Google OAuth2
```

---

## 5. 핵심 제약사항 (절대 위반 금지)

| 규칙 | 이유 |
|---|---|
| 대기 등록은 `WaitingLockFacade` 경유 필수 | Redisson 분산락으로 Race Condition 방지 |
| 물리 삭제 금지, `softDelete()` 사용 | 데이터 이력 보존 |
| 모든 예외: `BusinessException(ErrorCode.XXX)` | 통일된 에러 응답 형식 |
| 새 예외 클래스 생성 금지 | `ErrorCode` enum 항목 추가로 대체 |
| SSE 알림 전 `waitingRepository.flush()` 필수 | 상태 변경 DB 반영 후 알림 전송 순서 보장 |
| `Coordinate(longitude, latitude)` 순서 고정 | PostGIS WGS84 좌표계 규격 |

> 전체 금지 사항 목록은 `shrimp-rules.md` § 12 참조
