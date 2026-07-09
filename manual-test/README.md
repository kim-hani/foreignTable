# 실시간 상담 채팅(#43) 수동 테스트 가이드

`chat-test.html` 한 페이지로 REST(방 생성·수락·종료·읽음·이력)와 WebSocket(실시간 송수신)을 전 구간 테스트한다.

---

## 0. 사전 준비 — 인프라 & 앱 기동

```bash
# 프로젝트 루트에서
docker compose up -d db redis      # PostgreSQL + Redis
./gradlew bootRun                  # 앱 (localhost:8080)
```

> Redis가 떠 있어야 한다. 채팅 메시지는 Redis 채널(`chat:room:*`)을 거쳐 브로드캐스트된다.

---

## 1. 테스트 계정 준비 (최초 1회, Swagger에서)

`http://localhost:8080/swagger-ui.html` → **01. 인증(Auth) API**

**일반 사용자 2명** (`POST /api/v1/auth/signup`) — 고객 역할. 최소 1명, 담당자 배정 뒤 양방향 대화를 보려면 아래 ADMIN과 함께 쓴다.
```json
{ "loginId": "user01", "password": "Test1234!", "passwordCheck": "Test1234!",
  "email": "user01@test.com", "nickname": "고객01" }
```

**관리자 1명** (`POST /api/v1/auth/admin/signup`) — 상담원 역할. `adminKey`는 `.env`의 `ADMIN_SECRET_KEY` 값.
```json
{ "loginId": "admin01", "password": "Test1234!",
  "email": "admin01@test.com", "nickname": "상담원01", "adminKey": "<ADMIN_SECRET_KEY>" }
```

> 비밀번호 규칙: 영문+숫자+특수문자 포함 8~16자. 페이지 기본값(`Test1234!`)이 이 규칙을 만족한다.

---

## 2. 테스트 페이지 열기 — **반드시 localhost:3000**

CORS 허용 출처가 `http://localhost:3000`으로 고정돼 있어 `file://`로 열면 핸드셰이크가 거부된다.
`manual-test/` 폴더에서 정적 서버를 띄운다:

```bash
# 택 1: Python
cd manual-test && python -m http.server 3000

# 택 2: Node
npx serve manual-test -l 3000
```

브라우저에서 **http://localhost:3000/chat-test.html** 접속. 왼쪽 USER 패널, 오른쪽 ADMIN 패널이 보인다.

---

## 3. 시나리오 (권장 순서)

### ① 로그인
- USER 패널: `일반 로그인` → "토큰 발급됨"
- ADMIN 패널: `관리자 로그인` → "토큰 발급됨"

### ② 상담 요청 (USER)
- USER: **방 생성/상담원 연결** → `roomId`가 표시됨(예: 1). 상태 WAITING.
  - (챗봇 `needsAgent=true` 후 "상담원 연결" 버튼을 누른 상황에 해당)

### ③ 상담 수락 (ADMIN)
- ADMIN: 대기열 `WAITING` 선택 → **목록 조회** → 방금 만든 `roomId` 확인
- ADMIN: roomId 입력칸에 그 번호 입력 → **수락(claim)** → 상태 ACTIVE, 담당자 배정

### ④ WebSocket 연결 (양쪽)
- 양 패널 모두 roomId가 같은지 확인 후 각각 **연결 & 구독** → "연결됨"
  - USER 방 생성 후 roomId가 자동 세팅되고, ADMIN은 claim 시 세팅된다.

### ⑤ 실시간 대화
- USER 입력창에 메시지 → **전송**(또는 Enter) → **양쪽 로그에 `💬` 즉시 표시**
- ADMIN도 전송 → USER 로그에 표시. → **양방향 실시간 확인 ✅**

### ⑥ 읽음 처리
- ADMIN이 USER 메시지를 받은 뒤 **읽음 처리** → USER 로그에 `👁 상대방이 읽었습니다` (READ 이벤트) 표시

### ⑦ 시스템 메시지 & 종료
- 어느 쪽이든 **상담 종료** → 상대 로그에 `🔔 [시스템] 상담이 종료되었습니다` 표시
- 종료 후 메시지 전송 시도 → `✗ ... CH004 이미 종료된 상담 채팅방입니다`

### ⑧ 이력 조회
- **이력 조회** → 저장된 TALK 메시지 목록과 읽음 여부 확인 (SYSTEM/READ는 저장 안 되므로 안 나옴)

---

## 4. 확인 포인트 (기대 동작)

| 검증 항목 | 확인 방법 |
|---|---|
| STOMP JWT 인증 | 로그인 없이 연결 시 WebSocket 오류 / STOMP ERROR |
| 구독 권한(도청 방지) | 남의 roomId로 연결하면 구독 거부(참여자/ADMIN 아니면 CH002) |
| 동시 수락 경합 | 이미 claim된 방을 다시 claim → `CH003` |
| 종료 방 발신 차단 | 종료 후 전송 → `CH004` |
| 읽음 영수증 | 읽음 처리 시 상대에게 READ 이벤트 도달 |
| 방 생성 멱등 | 열린 방이 있을 때 다시 생성 → 같은 roomId 반환 |

---

## 5. (선택) Redis 릴레이 증명 — 2 인스턴스

단일 인스턴스로도 채팅은 되지만(같은 인메모리 브로커), 메시지는 항상 Redis를 거친다.
크로스 인스턴스 전달까지 증명하려면:

```bash
# 터미널 A
./gradlew bootRun                                   # 8080
# 터미널 B
./gradlew bootRun --args='--server.port=8081'       # 8081
# 터미널 C — 발행 관찰
redis-cli MONITOR | findstr chat:room
```

- USER 패널 WS는 `ws://localhost:8080/ws`, ADMIN 패널 WS는 `ws://localhost:8081/ws`로 각각 지정
- 서로 다른 인스턴스에 붙은 두 클라이언트가 메시지를 주고받으면 → Redis 릴레이가 동작하는 것
