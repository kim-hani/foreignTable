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

### 6.3 로그아웃 — `POST /api/v1/auth/logout`
```java
refreshTokenRepository.deleteById(String.valueOf(member.getId()));
```
→ 서버의 Refresh Token 삭제. 이후 재발급 시도 시 `findByValue` 실패로 차단.

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

### 7.2 provider별 응답 정규화 — CustomOAuth2UserService

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

## 8. 회원 관리 — MemberService

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
