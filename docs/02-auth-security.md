# 02. 인증 & 보안

> JWT 기반 Stateless 인증, RefreshToken 재발급, OAuth2 소셜 로그인(Google·Kakao·Naver), 회원 관리

---

## 1. 인증 아키텍처 개요

SmartWaiting은 **세션을 사용하지 않는(Stateless) JWT 인증**을 채택했습니다.

```
세션 방식 ❌            →           JWT 방식 ✅
서버가 세션 저장           토큰에 인증정보 담아 클라이언트가 보관
수평 확장 시 세션 공유 필요   서버는 토큰 검증만 → 무상태 → 확장 용이
```

### 토큰 2종 전략

| 토큰 | 유효기간 | 저장 위치 | 역할 |
|---|---|---|---|
| **Access Token** | 24시간(`86400000ms`) | 클라이언트 | API 요청 인증. `email`+`role` 클레임 포함 |
| **Refresh Token** | 7일(`604800000ms`) | **서버(DB/Redis)** + 클라이언트 | Access 만료 시 재발급용. 정보 없이 유효기간만 |

> Refresh Token을 서버에도 저장하는 이유: 로그아웃 시 서버에서 삭제해 **무효화**할 수 있어야 하기 때문. (순수 JWT는 만료 전 취소가 불가)

**왜 토큰을 2종으로 나누나**: 토큰 하나만 길게 쓰면 탈취 시 피해 기간이 길고, 짧게만 쓰면 사용자가 자주 다시 로그인해야 합니다. 그래서 **자주 노출되는 Access Token은 짧게(24시간)**, **재발급 전용 Refresh Token은 길게(7일)** 가져갑니다. Access는 매 API 요청에 실려 노출 빈도가 높으므로 만료를 짧게 두어 탈취 위험을 줄이고, Refresh는 재발급 때만 쓰여 노출이 적으므로 길게 둡니다. 여기에 Refresh를 **서버에도 저장**해 두면, 순수 JWT의 약점인 "만료 전 강제 무효화 불가"를 보완해 로그아웃·탈퇴 시 즉시 차단할 수 있습니다.

---

## 2. JWT 생성·검증 — JwtTokenProvider

```java
@Component
public class JwtTokenProvider {
    private Key key;  // HMAC-SHA256 서명 키

    @PostConstruct
    protected void init() {  // 시크릿을 Base64 인코딩 후 HMAC 키 생성
        byte[] keyBytes = Base64.getDecoder().decode(
            Base64.getEncoder().encodeToString(secretKey.getBytes()));
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String email, String role) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("role", role);          // 권한을 토큰에 담음
        // ... 발급시각/만료시각 설정 후 HS256 서명
    }
}
```

| 메서드 | 역할 |
|---|---|
| `createAccessToken(email, role)` | subject=email, claim=role 담아 서명 |
| `createRefreshToken()` | 정보 없이 만료기간만 가진 토큰 |
| `validateToken(token)` | 서명·만료 검증 (실패 시 `false`) |
| `getEmail(token)` / `getRole(token)` | 클레임 추출 |

**기술 선택**: `jjwt` 라이브러리 + HS256(대칭키) 서명. 단일 서버가 발급·검증을 모두 하므로 비대칭키(RS256)보다 간단한 대칭키 사용.

---

## 3. 요청 인증 흐름 — JwtAuthenticationFilter

모든 요청에서 한 번씩 실행되는 필터(`OncePerRequestFilter`)입니다.

```
요청 도착
  │
  ▼
Authorization 헤더에서 "Bearer {token}" 추출
  │
  ▼
validateToken(token) ── 실패/없음 ──▶ 인증 없이 통과 (이후 SecurityConfig가 차단)
  │ 성공
  ▼
email·role 추출 → UsernamePasswordAuthenticationToken 생성
  │              (권한: "ROLE_" + role)
  ▼
SecurityContextHolder에 인증 주입
  │
  ▼
다음 필터로 진행 → Controller에서 principal.getName()으로 email 사용 가능
```

```java
UsernamePasswordAuthenticationToken authentication =
    new UsernamePasswordAuthenticationToken(
        email, null,
        Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)));
SecurityContextHolder.getContext().setAuthentication(authentication);
```

> 이 필터는 `UsernamePasswordAuthenticationFilter` **앞에** 배치됨(`addFilterBefore`).

**흐름 설명** — 단계별로:

- **핵심 원칙**: "토큰이 없거나 틀려도 여기서 막지 않는다." 헤더에서 토큰을 꺼내 검증만 한다.
- **검증 성공 시**: 인증 정보를 `SecurityContextHolder`에 심고 다음 단계로 넘긴다.
- **검증 실패/토큰 없음**: 인증되지 않은 상태로 그대로 통과시킨다 → 실제 차단은 뒤이은 `SecurityConfig`의 권한 규칙(`authenticated()`/`hasRole(...)`)이 담당.
- **효과**: "인증(누구인가)"과 "인가(접근 가능한가)"의 책임이 분리되어, 공개 API(식당 조회)는 토큰 없이 통과하고 보호 API만 차단된다.
- **이후**: 통과한 요청은 Controller에서 `principal.getName()`으로 이메일을 꺼내 본인 확인에 사용한다.

---

## 4. 권한 매핑 — SecurityConfig

```java
http
  .csrf(disable)                                    // JWT라 CSRF 불필요
  .sessionManagement(STATELESS)                     // 세션 미사용
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .requestMatchers("/api/v1/auth/**").permitAll()              // 인증 API 공개
    .requestMatchers(GET, "/api/v1/stores/**").permitAll()      // 식당 조회 공개
    .requestMatchers(POST, "/api/v1/stores").hasRole("OWNER")   // 식당 등록은 점주만
    .requestMatchers(PUT/PATCH, "/api/v1/stores/**").hasRole("OWNER")
    .requestMatchers(DELETE, "/api/v1/stores/**").hasAnyRole("ADMIN","OWNER")
    .requestMatchers(PATCH, "/api/v1/waitings/*/status").hasRole("OWNER") // 대기열 제어는 점주만
    .anyRequest().authenticated())                  // 나머지는 로그인 필수
  .oauth2Login(...)                                 // 소셜 로그인 연결
  .exceptionHandling(...)                           // 401/403 커스텀 핸들러
  .addFilterBefore(new JwtAuthenticationFilter(...), ...);
```

### 권한 매트릭스

| 엔드포인트 | 권한 |
|---|---|
| `/api/v1/auth/**`, Swagger | 누구나 |
| `GET /api/v1/stores/**` | 누구나 (비로그인 검색 허용) |
| `POST/PUT/PATCH /api/v1/stores/**` | OWNER |
| `DELETE /api/v1/stores/**` | OWNER 또는 ADMIN |
| `PATCH /api/v1/waitings/*/status` | OWNER (대기열 호출/착석/노쇼) |
| 그 외 전부 | 인증된 사용자 |

### 인증 실패 처리
- `JwtAuthenticationEntryPoint` → **401** (토큰 없음/만료)
- `JwtAccessDeniedHandler` → **403** (권한 부족)

---

## 5. 회원가입 & 로그인 — AuthService

### 5.1 3가지 가입 경로

| 메서드 | Role | 특이사항 |
|---|---|---|
| `signupMember` | USER | 일반 손님 |
| `signupOwner` | OWNER | 식당 점주 |
| `signupAdmin` | ADMIN | **관리자 시크릿 키(`adminKey`) 검증 필수** |

**공통 가입 검증(`validation`)**:
1. 이메일 중복 → `EMAIL_ALREADY_EXISTS`
2. 로그인ID 중복 → `LOGIN_ID_ALREADY_EXISTS`
3. 비밀번호 == 비밀번호확인 → 불일치 시 `PASSWORD_MISMATCH`
4. **BCrypt로 해싱** 후 저장 (`provider="general"`)

### 5.2 로그인 → 토큰 발급 파이프라인

```
loginId/password 입력
  │
  ▼ validateMember()
findByLoginId ── 없음 ──▶ INVALID_IDPASSWORD
  │ 있음
  ▼
passwordEncoder.matches() ── 불일치 ──▶ INVALID_IDPASSWORD
  │ 일치
  ▼ createTokenResponse()
Access Token 생성 (email + role)
Refresh Token 생성
  │
  ▼
RefreshTokenService.saveOrUpdate(memberId, refreshToken)  ── 서버 저장
  │
  ▼
SignInResponseDto 반환 { grantType:"Bearer", accessToken, refreshToken, expiresIn }
```

**흐름 설명** — 단계별로:

- **① 회원 조회**: `loginId`로 회원을 찾는다 (없으면 실패).
- **② 비밀번호 대조**: `passwordEncoder.matches()`로 입력값과 저장된 BCrypt 해시를 비교.
- **보안 포인트**: 아이디 없음·비밀번호 불일치를 **같은 에러(`INVALID_IDPASSWORD`)**로 응답 → "그 아이디는 존재한다"는 정보를 공격자에게 노출하지 않기 위함.
- **③ 토큰 발급·저장**: 검증 통과 시 Access·Refresh를 발급하고, **Refresh만 서버에 저장**(`saveOrUpdate`)한 뒤 두 토큰을 함께 반환.
- **의미**: 이 서버 저장이 이후 재발급·로그아웃에서 토큰을 통제하는 근거가 된다.

> 관리자 로그인(`signinAdmin`)은 추가로 `role == ADMIN` 검증 후 통과.

---

## 6. 토큰 재발급 & 로그아웃

### 6.1 RefreshToken 저장 — RefreshTokenService

```java
public void saveOrUpdate(String key, String token) {  // key = memberId
    RefreshToken refreshToken = refreshTokenRepository.findByKey(key)
        .orElse(RefreshToken.builder().key(key).value(token).build());
    refreshToken.updateValue(token);   // 있으면 갱신, 없으면 신규
    refreshTokenRepository.save(refreshToken);
}
```
- `RefreshToken` 엔티티: `key`(memberId) + `value`(토큰 문자열)
- 한 회원당 토큰 1개 유지 → 재로그인 시 기존 토큰 덮어쓰기

### 6.2 재발급 흐름 — `POST /api/v1/auth/reissue`

```
Refresh Token 제출
  │
  ▼
validateToken() ── 무효/만료 ──▶ INVALID_TOKEN
  │ 유효
  ▼
DB에서 findByValue() ── 없음 ──▶ INVALID_TOKEN  (서버에 없는 토큰 = 무효화됨)
  │ 있음
  ▼
저장된 key(memberId)로 회원 조회 → 탈퇴 회원이면 MEMBER_NOT_FOUND
  │
  ▼
createTokenResponse() ── Access + Refresh 둘 다 새로 발급 (Refresh Rotation)
```

**흐름 설명** — 단계별로:

- **① 1차 검증(서명·만료)**: 제출된 토큰이 서명·만료상 유효한지 확인.
- **② 2차 검증(서버 존재)**: 그 토큰이 서버 DB에도 실제로 있는지 확인 → **핵심 단계**. 로그아웃하면 서버에서 토큰을 지우므로, 토큰이 아직 만료 전이라 1차는 통과해도 `findByValue`에서 걸러진다. "순수 JWT는 만료 전 취소가 불가"한 약점을 메우는 지점.
- **③ 회원 조회**: 저장된 `key`(memberId)로 회원 조회 (탈퇴 회원이면 차단).
- **④ 재발급(Rotation)**: Access·Refresh를 **둘 다 새로** 발급. 한 번 쓰인 Refresh를 폐기해 탈취·재사용 위험을 줄이기 위함.

### 6.3 로그아웃 — `POST /api/v1/auth/logout`
```java
refreshTokenRepository.deleteById(String.valueOf(member.getId()));
```
→ 서버의 Refresh Token 삭제. 이후 재발급 시도 시 `findByValue` 실패로 차단.

**흐름 설명**: 로그아웃은 "토큰을 서버에서 지우는 것"이 전부입니다. 클라이언트가 가진 Access Token은 만료(최대 24시간) 전까지는 여전히 유효하지만, **Refresh가 사라졌으므로 만료 후 재발급이 불가능**해져 자연스럽게 세션이 끝납니다. 서버 측 Refresh 저장이 있었기에 가능한 통제 방식입니다.

---

## 7. OAuth2 소셜 로그인 (Google·Kakao·Naver)

### 7.1 전체 흐름

```
사용자 → 소셜 로그인 버튼 클릭
  │
  ▼  Spring Security OAuth2 표준 흐름
소셜 인증 서버(구글/카카오/네이버)에서 로그인 & 동의
  │
  ▼  인가 코드 → 액세스 토큰 → 사용자 정보 요청
CustomOAuth2UserService.loadUser()  ── provider별 응답 정규화 + 회원 자동 가입/조회
  │
  ▼  인증 성공
OAuth2SuccessHandler.onAuthenticationSuccess()  ── JWT 발급
  │
  ▼
프론트엔드로 리다이렉트 (?token=...&refreshToken=...)
```

**흐름 설명** — 단계별로:

- **① 소셜 인증**: 사용자가 버튼을 누르면 구글·카카오·네이버 인증 서버에서 로그인·동의가 이뤄진다.
- **② 정보 수신**: Spring이 인가 코드 → 액세스 토큰 → 사용자 정보 순으로 받아온다 (OAuth2 표준 흐름).
- **③ 정규화·자동 가입**: `CustomOAuth2UserService`가 provider마다 제각각인 응답 구조를 한 형태로 정규화하고, 처음 온 사용자면 USER로 자동 가입(§7.2).
- **④ 자체 JWT 발급**: `OAuth2SuccessHandler`가 **이 서비스의 JWT**를 발급해 프론트로 리다이렉트.
- **통합 효과**: 소셜 로그인은 입구만 외부에 맡기고, 이후 모든 API 인증은 일반 로그인과 **완전히 동일한 JWT 체계**로 통합된다.

소셜마다 사용자 정보 JSON 구조가 다릅니다. 이를 **하나의 형태로 정규화**합니다.

| Provider | 이메일 위치 | 이름 위치 |
|---|---|---|
| **Google** | `attributes.email` | `attributes.name` |
| **Kakao** | `kakao_account.email` | `properties.nickname` |
| **Naver** | `response.email` | `response.name` |

```java
switch (provider) {
    case "kakao" -> {
        Map kakaoAccount = (Map) attributes.get("kakao_account");
        email = (String) kakaoAccount.get("email");
        // SuccessHandler가 attributes.get("email")로 접근하므로 최상위에 주입
        normalizedAttributes.put("email", email);
    }
    case "naver" -> { ... response 맵에서 추출 ... }
    default -> { ... google: 최상위에서 바로 추출 ... }
}
Member member = saveOrUpdate(email, name, provider);  // 없으면 USER로 자동 가입
```

**핵심 설계**: 카카오/네이버의 중첩된 이메일을 최상위 `email` 키로 끌어올려서, 후속 `OAuth2SuccessHandler`가 provider 구분 없이 `attributes.get("email")` 하나로 처리 가능하게 함.

### 7.3 소셜 로그인 후 JWT 발급 — OAuth2SuccessHandler

```java
public void onAuthenticationSuccess(...) {
    String email = (String) oAuth2User.getAttributes().get("email");
    Member member = memberRepository.findByEmail(email).orElseThrow(...);

    String accessToken = jwtTokenProvider.createAccessToken(email, member.getRole().name());
    String refreshToken = jwtTokenProvider.createRefreshToken();
    refreshTokenService.saveOrUpdate(String.valueOf(member.getId()), refreshToken);

    // 토큰을 쿼리 파라미터로 프론트에 전달
    String targetUrl = "http://localhost:3000/oauth/callback?token=...&refreshToken=...";
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
}
```
→ **소셜 로그인도 결국 자체 JWT로 통합**. 이후 모든 API는 동일한 JWT 인증 사용.

---

## 8. 발급받은 토큰을 요청에 사용하는 전체 흐름

일반 로그인(§5)이든 소셜 로그인(§7)이든, 결국 클라이언트는 **Access Token + Refresh Token 한 쌍**을 손에 쥡니다. 이 토큰들이 실제 API 요청에서 어떻게 쓰이는지를, 발급 직후부터 만료·재발급까지 한 줄기로 정리합니다.

### 8.1 토큰 보관 위치

| 토큰 | 클라이언트 보관 | 서버 보관 |
|---|---|---|
| **Access Token** | 메모리/스토리지에 보관 후 매 요청에 첨부 | ❌ (검증만, 저장 안 함) |
| **Refresh Token** | 재발급 때만 꺼내 쓰도록 안전하게 보관 | ✅ (DB/Redis, 무효화 근거) |

> 로그인 응답 또는 OAuth 콜백(`?token=...&refreshToken=...`)으로 받은 두 토큰을 클라이언트가 저장하는 것이 시작점입니다.

### 8.2 일반 요청 — Access Token으로 인증

```
클라이언트
  │  GET /api/v1/waitings/my
  │  Authorization: Bearer {accessToken}      ← 매 요청 헤더에 첨부
  ▼
JwtAuthenticationFilter (§3)
  │  ① "Bearer " 떼고 토큰 추출
  │  ② validateToken() — 서명·만료 검증
  │  ③ email·role 추출 → SecurityContext에 인증 주입
  ▼
SecurityConfig 권한 검사 (§4)
  │  해당 URL·메서드에 필요한 Role 확인
  ▼
Controller
  │  principal.getName() = email → 본인 데이터 조회
  ▼
정상 응답 (200)
```

**흐름 설명** — Access Token 하나로 "누구인지 + 접근 가능한지"가 결정된다:

- **① 첨부**: 클라이언트는 모든 보호 API 요청의 `Authorization` 헤더에 `Bearer {accessToken}`을 싣는다.
- **② 검증**: `JwtAuthenticationFilter`가 토큰의 서명·만료를 확인한다 (서버는 Access를 저장하지 않으므로 DB 조회 없이 검증만으로 끝 → Stateless).
- **③ 인증 주입**: 유효하면 `email`·`role`을 꺼내 `SecurityContext`에 심는다.
- **④ 인가**: `SecurityConfig`가 그 URL에 필요한 권한을 확인한다 (예: `PATCH /waitings/*/status`는 OWNER).
- **⑤ 처리**: Controller가 `principal.getName()`(email)으로 요청자를 식별해 본인 데이터만 처리한다.

### 8.3 Access 만료 — Refresh Token으로 재발급 후 재시도

Access Token은 24시간이면 만료됩니다. 만료된 토큰으로 요청하면 `JwtAuthenticationEntryPoint`가 **401**을 돌려주고(§4), 이때 클라이언트가 **재발급(§6.2)**으로 복구합니다.

```
① 만료된 Access로 API 요청
        │
        ▼
② 서버 401 응답 (토큰 만료)
        │
        ▼
③ 클라이언트: POST /api/v1/auth/reissue { refreshToken }   ← Refresh를 꺼내 재발급 요청
        │
        ▼
④ 서버: Refresh 검증(서명+만료) + DB 존재 확인 → 새 Access·Refresh 발급 (Rotation, §6.2)
        │
        ▼
⑤ 클라이언트: 새 Access로 ① 요청을 그대로 재시도 → 성공
```

**흐름 설명** — "401을 만나면 한 번 재발급하고 다시 보낸다"는 자동 복구 루프다:

- **② 만료 감지**: 보호 API가 401을 반환하면 클라이언트는 "Access가 만료됐다"고 판단한다.
- **③ 재발급 요청**: 저장해 둔 Refresh Token을 `/auth/reissue`로 보낸다.
- **④ 서버 검증·발급**: Refresh의 서명·만료(1차)와 **서버 DB 존재 여부(2차)**를 확인한 뒤 새 토큰 쌍을 발급한다. (로그아웃했다면 DB에서 지워져 2차에서 차단 → 재로그인 필요)
- **⑤ 재시도**: 클라이언트는 새 Access로 원래 요청을 다시 보내 정상 응답을 받는다. 사용자는 끊김을 거의 느끼지 못한다.

> 보통 이 401 감지 → 재발급 → 재시도 로직은 프론트엔드의 HTTP 인터셉터(예: axios interceptor)에 한 번 구현해 전 API에 공통 적용합니다.

### 8.4 한 장으로 보는 토큰 생애주기

```
[발급]   로그인/소셜로그인 → Access(24h) + Refresh(7d) 수령
   │
[사용]   매 요청 Authorization: Bearer Access → 필터 검증 → 인가 → 처리
   │
[갱신]   Access 만료(401) → Refresh로 reissue → 새 토큰으로 재시도
   │
[종료]   로그아웃 → 서버에서 Refresh 삭제 → 이후 재발급 차단 (§6.3)
```

---

## 9. 회원 관리 — MemberService

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 내 정보 조회 | `GET /api/v1/members/me` | 이메일로 조회 |
| 닉네임 수정 | `PUT /api/v1/members/me` | 공백 아닐 때만 변경 |
| 비밀번호 변경 | `PATCH /api/v1/members/me/password` | 현재 비번 확인 + 신규 비번 확인 일치 |
| **FCM 토큰 갱신** | `PATCH /api/v1/members/me/fcm-token` | 앱 재설치/만료 시 푸시 토큰 갱신 |
| 회원 탈퇴 | `DELETE /api/v1/members/me` | soft delete + 이메일 마스킹 + RefreshToken 삭제 |

### Member 엔티티 핵심 필드·메서드
```java
@SQLRestriction("is_deleted = false")   // 조회 시 탈퇴 회원 자동 제외
public class Member extends BaseEntity {
    private String email;       // unique
    private String nickname;
    private UserRole role;       // USER / OWNER / ADMIN
    private String loginId;     // unique (소셜 로그인은 null)
    private String password;    // BCrypt 해시 (소셜은 null)
    private String provider;    // general / google / kakao / naver
    private int noShowCount;    // 노쇼 누적 횟수
    private String fcmToken;    // 푸시 알림 대상 토큰

    public boolean isBlacklisted() {     // 노쇼 3회 이상 = 블랙리스트
        return this.noShowCount >= 3;
    }
    public void incrementNoShowCount() { this.noShowCount++; }
}
```

> **블랙리스트**: 노쇼가 3회 누적되면 `isBlacklisted()=true`가 되어 웨이팅 등록이 차단됨([03-waiting](./03-waiting.md) §6 참고).
