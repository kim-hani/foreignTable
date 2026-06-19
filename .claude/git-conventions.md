# Git Conventions

---

## Commit Message

### Format

```
{branch-name}: {Summary in English}

- {Detail 1}
- {Detail 2}
- {Detail 3}
```

### Rules

- **첫 줄**: `브랜치명: 영어 요약` — 50자 이내
- **상세 항목**: 변경 이유·내용을 bullet로 나열, 각 항목은 동사 원형으로 시작
- 브랜치명은 슬래시 포함 전체 이름 사용 (ex. `feat/OAuth`, `fix/waiting-lock`)

### Example

```
feat/OAuth: Add Kakao and Naver OAuth2 social login support

- Add kakao/naver registration and provider config in application.yml
- Implement provider-based branching in CustomOAuth2UserService
- Normalize attributes map so OAuth2SuccessHandler requires no changes
- Store provider field (kakao/naver) on Member entity creation
```

---

## Pull Request

### Format

```
{Title}
{One-paragraph English description of the PR purpose and impact}

### Changes
- {Change 1}
- {Change 2}
- {Change 3}

### Notes
- {Note 1}
- {Note 2}

---
{한국어 제목}
{PR 목적과 영향을 설명하는 한 문단}

### 주요 변경 사항
- {변경 사항 1}
- {변경 사항 2}
- {변경 사항 3}

### 비고
- {비고 1}
- {비고 2}
```

### Example

```
Implement optimized SSE real-time alerts and FCM push notifications
This pull request introduces an optimized real-time notification system using SSE
and FCM, ensuring accurate waiting status updates and efficient push delivery.
The update enhances performance, prevents unnecessary alerts, and improves user
engagement through automated review requests.

### Changes
- Established SSE pipeline for real-time waiting status updates
- Optimized "3rd in line" alert logic to trigger only on actual queue shifts
- Prevented memory leaks by explicitly closing SSE connections on terminal states (SEATED, CANCEL, NOSHOW)
- Added Spring @Scheduled cron job to target users exactly 1 hour after seating
- Integrated Firebase Admin SDK (FCM) to send automated review request push notifications

### Notes
- Reduces unnecessary notifications and improves accuracy of real-time alerts
- Prevents resource leaks through proper SSE lifecycle management
- Enhances user engagement with automated review requests
- Improves system reliability and scalability

---

SSE 기반 실시간 알림 및 FCM 푸시 알림 기능 최적화
이 PR은 SSE 기반 실시간 대기 상태 알림과 FCM 푸시 알림을 최적화하여
정확한 알림 전달과 시스템 안정성을 강화합니다.
또한 자동 리뷰 요청 기능을 통해 사용자 참여도를 높입니다.

### 주요 변경 사항
- 실시간 대기 상태 업데이트를 위한 SSE 파이프라인 구축
- 실제 줄 이동 시에만 동작하도록 '3번째 남았어요' 알림 로직 최적화
- SEATED / CANCEL / NOSHOW 상태에서 SSE 연결 명시적 종료로 메모리 누수 방지
- 착석 후 정확히 1시간 뒤 사용자 타겟팅을 위한 Spring 스케줄러 추가
- Firebase Admin SDK(FCM) 연동으로 자동 리뷰 요청 푸시 알림 발송

### 비고
- 과도한 알림을 방지하여 사용자 경험 개선
- SSE 연결 관리 강화로 시스템 안정성 향상
- 자동 리뷰 요청을 통한 사용자 참여도 증가
- 확장성과 유지보수성을 고려한 구조 개선
```
