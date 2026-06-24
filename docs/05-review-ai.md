# 05. 리뷰 & AI 고도화

> 리뷰 CRUD, **AI 3줄 요약(Gemini)**, **이미지 업로드(S3)**, **평균 별점 자동 집계**

리뷰 도메인은 기본 CRUD 위에 **3가지 고도화 기능**(AI 요약·이미지·평균 별점)이 얹혀 있습니다.

---

## 1. Review 엔티티

```java
public class Review extends BaseEntity {
    @ManyToOne(LAZY) private Store store;
    @ManyToOne(LAZY) private Member member;

    @OneToOne(LAZY)
    @JoinColumn(name = "waiting_id", unique = true)  // 웨이팅 1건당 리뷰 1개
    private Waiting waiting;

    private String content;
    private int rating;          // 별점 1~5

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> imageUrls;  // 리뷰 이미지 URL 목록 (JSONB)
}
```

### 핵심 설계
- **`waiting`과 1:1 (unique)**: "실제로 웨이팅하고 방문한 사람"만 리뷰 가능 + 웨이팅당 리뷰 1건 강제
- **`imageUrls`를 JSONB로**: 이미지 URL 배열을 별도 테이블 없이 한 컬럼에 저장

---

## 2. 리뷰 작성 — createReview

`POST /api/v1/reviews` — 까다로운 검증 5단계를 거칩니다.

```
리뷰 작성 요청 (waitingId, content, rating, imageUrls)
  │
  ① 회원 존재 확인 ──────────────▶ MEMBER_NOT_FOUND
  │
  ② 웨이팅 존재 확인 ────────────▶ WAITING_NOT_FOUND
  │
  ③ 본인의 웨이팅인가? ──────────▶ NOT_YOUR_WAITING
  │
  ④ SEATED(착석) 상태인가? ──────▶ REVIEW_UNAUTHORIZED_VISIT
  │   (실제 방문자만 리뷰 가능)
  │
  ⑤ 착석 후 48시간 이내인가? ────▶ REVIEW_TIME_EXPIRED
  │   (waiting.updatedAt + 48h)
  │
  ⑥ 이미 리뷰 썼는가? ───────────▶ REVIEW_ALREADY_EXISTS
  │   (existsByWaitingId)
  │
  ▼
Review 저장
  │
  ▼
store.updateRatingOnCreate(rating)  ★ 평균 별점 자동 갱신 (§5)
  │
  ▼
reviewId 반환
```

**흐름 설명** — 6단계 검증은 "**실제로 방문한 사람만, 적절한 시기에, 한 번만**" 쓰도록 거르는 깔때기다:

- **① ② ③ 소유 확인**: 회원·웨이팅이 존재하고, 그 웨이팅이 본인 것인지 확인.
- **④ 방문 확인 (핵심)**: **SEATED(착석) 상태인지** 확인 → 예약만 하고 안 온 사람·취소한 사람의 허위 리뷰 차단.
- **⑤ 시기 확인**: 착석 후 48시간이 지났는지 확인.
- **⑥ 중복 확인**: 이미 리뷰를 썼는지 확인.
- **저장·집계**: 통과 시 저장하고, 같은 트랜잭션에서 가게 평균 별점을 갱신(§5).

> 검증을 "방문 여부 → 시기 → 중복" 순으로 쌓아, 각 관문이 신뢰성을 한 겹씩 보장한다.

### 신뢰성 검증 설계
| 검증 | 목적 |
|---|---|
| SEATED 상태 확인 | **방문 안 한 사람의 허위 리뷰 차단** |
| 48시간 제한 | 기억이 생생할 때만 작성 → 리뷰 신뢰성 |
| waiting당 1건(unique) | 어뷰징(중복 리뷰) 방지 |

> 48시간 판정은 `BaseEntity.updatedAt`(착석으로 상태 변경된 시각)을 기준으로 함. JPA Auditing이 자동 기록한 값 활용.

---

## 3. 리뷰 조회 & 삭제

| 기능 | 엔드포인트 | 설명 |
|---|---|---|
| 가게별 리뷰 | `GET /api/v1/reviews/store/{storeId}` | 최신순, 페이징(Slice) |
| 내 리뷰 | `GET /api/v1/reviews/my` | 최신순, 페이징 |
| 삭제 | `DELETE /api/v1/reviews/{reviewId}` | **작성자 본인만** |

```java
// 삭제 시 평균 별점도 역산
public void deleteReview(Long reviewId, String email) {
    // 본인 확인 (NOT_YOUR_REVIEW)
    review.getStore().updateRatingOnDelete(review.getRating());  // ★ 평균 갱신
    reviewRepository.delete(review);
}
```

> 응답 DTO(`ReviewResponseDto`): reviewId, content, rating, 작성자 닉네임, 작성일, 이미지 URL 목록.

---

## 4. ⭐ AI 리뷰 3줄 요약 — AIReviewService

리뷰가 많은 가게의 리뷰를 **Gemini로 맛/분위기/서비스 3줄 요약**하는 고도화 기능.

`GET /api/v1/reviews/summary/{storeId}`

### 4.1 처리 파이프라인 (7단계)

```
요청: GET /reviews/summary/{storeId}
  │
  ① Redis 캐시 조회 (key: "aiSummary::{storeId}")
  │     ├─ Hit  ──▶ 캐시 즉시 반환 (AI 호출 X, 비용 0)
  │     └─ Miss ──▼
  │
  ② 리뷰 개수 집계 (countByStoreId)
  │
  ③ 50개 미만? ──▶ "리뷰가 50개 이상 모이면 AI 요약이 제공됩니다." 반환 (AI 호출 X)
  │     50개 이상 ▼
  │
  ④ 최신 리뷰 50건 조회 → content를 줄바꿈으로 연결
  │
  ⑤ Gemini 호출 (프롬프트: 맛/분위기/서비스 각 1줄, ✔️ 기호, 존댓말)
  │
  ⑥ 동적 TTL 계산 (리뷰 많을수록 짧게)
  │
  ⑦ Redis에 결과 캐싱 → 요약 반환
```

**흐름 설명** — 이 파이프라인의 목표는 **"AI를 최대한 부르지 않는 것"**이다 (Gemini 호출은 느리고 비싸므로):

- **① 캐시 게이트**: 먼저 Redis 캐시를 본다 → 있으면 AI를 건너뛰고 즉시 반환(비용 0).
- **② ③ 개수 게이트**: 캐시가 없을 때만 리뷰 수를 센다 → **50개 미만이면 요약 자체를 안 함**(표본이 적으면 신뢰도↓·비용 낭비).
- **④ ⑤ AI 호출**: 50개 이상이면 최신 50건을 모아 Gemini에 맛/분위기/서비스 3줄 요약 요청.
- **⑥ ⑦ 캐싱**: 결과를 **리뷰가 많을수록 짧은 TTL**로 저장 후 반환.

> 캐시 게이트 → 개수 게이트 → AI 호출 두 겹의 거름망 덕에, 반복 요청은 캐시가 흡수하고 AI는 꼭 필요할 때만 호출된다.

### 4.2 코드

```java
@Transactional(readOnly = true)
public String getAIReviewSummary(Long storeId) {
    String cacheKey = "aiSummary::" + storeId;

    // ① 캐시 히트 → AI 호출 없이 반환
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;

    // ② ③ 50개 미만이면 요약 안 함
    long total = reviewRepository.countByStoreId(storeId);
    if (total < 50) return "리뷰가 50개 이상 모이면 AI 요약이 제공됩니다.";

    // ④ 최신 50건 수집
    String aggregated = reviewRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId)
        .stream().map(Review::getContent).collect(Collectors.joining("\n- "));

    // ⑤ 프롬프트 → Gemini 호출
    String prompt = String.format(
        "너는 맛집 리뷰 전문 분석가야...\n[리뷰 원본]\n%s\n\n" +
        "정확히 3줄로 요약: 맛/분위기/서비스 각 1줄, '✔️'로 시작, 존댓말", aggregated);
    String summary = chatClient.prompt().user(prompt).call().content();

    // ⑥ 동적 TTL
    long ttlDays = total >= 500 ? 3 : total >= 300 ? 5 : total >= 100 ? 7 : 10;

    // ⑦ 캐싱
    redisTemplate.opsForValue().set(cacheKey, summary, Duration.ofDays(ttlDays));
    return summary;
}
```

### 4.3 동적 TTL 전략

| 리뷰 수 | 캐시 TTL | 근거 |
|---|---|---|
| 50~99 | **10일** | 리뷰가 천천히 쌓임 → 오래 캐시 |
| 100~299 | **7일** | |
| 300~499 | **5일** | |
| 500+ | **3일** | 활발한 가게 → 리뷰 자주 변함 → 자주 갱신 |

**핵심 아이디어**: 리뷰가 활발한 가게일수록 요약이 빨리 낡으므로 TTL을 짧게 → **신선도와 AI 비용의 균형**.

### 4.4 기술 선택 이유

| 결정 | 이유 |
|---|---|
| **Gemini 2.5 Flash** | 요약 작업에 충분한 성능 + Pro 대비 저비용·고속 |
| **Redis 캐싱** | AI 호출은 느리고 비쌈 → 한 번 요약하면 재사용. 같은 가게 반복 요청을 캐시로 흡수 |
| **50개 최소 기준** | 표본이 적으면 요약 신뢰도 낮음 + 불필요한 AI 비용 차단 |
| **최신 50건만** | 토큰 비용 제한 + 최근 트렌드 반영 |

> ⚠️ **현재 한계**: 리뷰 작성/삭제 시 캐시를 즉시 무효화하지 않음(TTL 만료까지 유지). AI 비용 절감을 위한 의도적 트레이드오프.

---

## 5. ⭐ 평균 별점 자동 집계

가게 단건 조회 시 평균 별점을 보여주기 위해 `Store`에 집계값을 **실시간 유지**합니다. (네이버 지도처럼 가게 조회 시점에 평균 노출)

### 5.1 Store 집계 필드 & 메서드

```java
public class Store extends BaseEntity {
    @Builder.Default private Double averageRating = 0.0;
    @Builder.Default private Integer reviewCount = 0;

    // 리뷰 작성 시: 가중 평균으로 증산
    public void updateRatingOnCreate(int newRating) {
        this.averageRating = (this.averageRating * this.reviewCount + newRating)
                             / (this.reviewCount + 1);
        this.reviewCount++;
    }

    // 리뷰 삭제 시: 역산 (마지막 1건이면 0으로 초기화)
    public void updateRatingOnDelete(int deletedRating) {
        if (this.reviewCount <= 1) {
            this.averageRating = 0.0;
            this.reviewCount = 0;
        } else {
            this.averageRating = (this.averageRating * this.reviewCount - deletedRating)
                                 / (this.reviewCount - 1);
            this.reviewCount--;
        }
    }
}
```

### 5.2 가중 평균 공식

```
작성: 새평균 = (기존평균 × 기존개수 + 새별점) / (기존개수 + 1)
삭제: 새평균 = (기존평균 × 기존개수 - 삭제별점) / (기존개수 - 1)
```

| 시점 | averageRating | reviewCount |
|---|---|---|
| 초기 | 0.0 | 0 |
| 별점 5 작성 | (0×0+5)/1 = **5.0** | 1 |
| 별점 3 작성 | (5×1+3)/2 = **4.0** | 2 |
| 별점 3 삭제 | (4×2-3)/1 = **5.0** | 1 |

> 첫 리뷰(1개)부터 즉시 집계 시작. AI 요약의 50개 조건과는 **완전히 별개**.

### 5.3 동작 원리: 더티 체킹

```
ReviewService.createReview()  (@Transactional)
  │
  ▼
waiting.getStore() ── 영속 상태(managed)의 Store 엔티티
  │
  ▼
store.updateRatingOnCreate(rating) ── 필드 변경
  │
  ▼
트랜잭션 커밋 → JPA 더티 체킹이 자동 UPDATE  ★ 별도 save() 불필요
```

**왜 이벤트(@TransactionalEventListener)가 아닌 직접 호출인가?**
프로젝트 전체가 "서비스 직접 호출" 패턴으로 일관되어 있고, 이벤트 인프라가 없음. 새 아키텍처 도입보다 기존 패턴 유지가 일관성 측면에서 유리. 같은 트랜잭션 내 더티 체킹으로 충분히 원자적.

### 5.4 응답 노출
`StoreResponseDto`에 `averageRating`, `reviewCount` 포함 → `GET /api/v1/stores/{id}` 응답에 표시.

---

## 6. ⭐ 이미지 업로드 (AWS S3) — UploadService

리뷰에 첨부할 이미지를 S3에 업로드하고 URL을 반환하는 기능.

`POST /api/v1/uploads/review-image` (multipart/form-data)

### 6.1 업로드 정책

```java
private static final long MAX_SIZE = 10 * 1024 * 1024L;   // 장당 10MB
private static final int MAX_COUNT = 5;                     // 최대 5장
private static final Set<String> ALLOWED_TYPES =
    Set.of("image/jpeg", "image/png", "image/webp");        // 허용 형식
```

### 6.2 업로드 파이프라인

```
multipart 파일 목록 수신
  │
  ① 파일 개수 ≤ 5 ──────────────▶ 초과 시 TOO_MANY_IMAGES
  │
  ▼ (각 파일마다)
  ② Content-Type 검증 ──────────▶ 미허용 시 INVALID_IMAGE_TYPE (jpg/png/webp만)
  │
  ③ 파일 크기 ≤ 10MB ───────────▶ 초과 시 IMAGE_TOO_LARGE
  │
  ④ UUID 기반 고유 키 생성: "reviews/{uuid}.{ext}"
  │
  ⑤ S3 putObject 업로드 ────────▶ 실패 시 IMAGE_UPLOAD_FAILED
  │
  ▼
"https://{bucket}.s3.{region}.amazonaws.com/{key}" URL 목록 반환
```

**흐름 설명** — **개수 → 형식 → 크기** 순으로 검증한 뒤 S3에 올린다:

- **① 개수 검증**: 전체 파일 수가 5장을 넘는지 한 번에 거름 (비용 싼 검사라 앞에 둠).
- **② ③ 형식·크기 검증**: 파일마다 Content-Type(jpg/png/webp)과 10MB 크기 확인.
- **④ ⑤ 저장**: 통과한 파일을 **UUID로 새 키**를 만들어 S3에 저장 → 원본 파일명을 안 쓰는 이유는 이름 충돌·한글·악성 경로(`../`) 차단.
- **반환·연결**: 접근 가능한 URL 목록만 돌려주고, 이 URL들은 리뷰 작성 요청에서 `imageUrls`로 전달(§6.5).

> **업로드와 리뷰 작성을 분리** → 이미지는 먼저 안전하게 올려두고, 리뷰는 URL만 참조한다.

### 6.3 코드

```java
public List<String> uploadReviewImages(List<MultipartFile> files) {
    if (files.size() > MAX_COUNT) throw new BusinessException(TOO_MANY_IMAGES);
    List<String> urls = new ArrayList<>();
    for (MultipartFile file : files) urls.add(upload(file));
    return urls;
}

private String upload(MultipartFile file) {
    if (!ALLOWED_TYPES.contains(file.getContentType())) throw ...(INVALID_IMAGE_TYPE);
    if (file.getSize() > MAX_SIZE) throw ...(IMAGE_TOO_LARGE);

    String key = "reviews/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    s3Client.putObject(PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(contentType)
            .contentLength(file.getSize()).build(),
        RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
}
```

### 6.4 설계 포인트

| 결정 | 이유 |
|---|---|
| **UUID 파일명** | 사용자 파일명 충돌·한글·악성 경로 방지 |
| **서버 경유 업로드** | 서버가 형식·크기를 검증한 후 S3 저장 → 무분별한 업로드 차단 |
| **S3 객체 스토리지** | 이미지를 DB·서버 디스크에 두지 않음 → 확장성·내구성 |
| **업로드/리뷰작성 분리** | 먼저 이미지 업로드 → URL 받아서 → 리뷰 작성 시 imageUrls로 전달 |

### 6.5 업로드 → 리뷰 연결 흐름

```
1. POST /uploads/review-image  (이미지 파일들)
   → ["https://...a.jpg", "https://...b.jpg"]  URL 목록 수신
2. POST /reviews  { waitingId, content, rating, imageUrls: [위 URL들] }
   → Review.imageUrls(JSONB)에 저장
```

> 💡 **활성화 조건**: AWS IAM 키·S3 버킷 발급 후 `.env`에 `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `S3_BUCKET_NAME`(리전 `ap-northeast-2`) 입력 필요. 코드는 완성 상태.

---

## 7. 리뷰 도메인 API 요약

| 엔드포인트 | 메서드 | 권한 | 설명 |
|---|---|---|---|
| `/api/v1/reviews` | POST | USER(방문자) | 리뷰 작성 |
| `/api/v1/reviews/store/{storeId}` | GET | 누구나 | 가게 리뷰 목록 |
| `/api/v1/reviews/my` | GET | USER | 내 리뷰 목록 |
| `/api/v1/reviews/{reviewId}` | DELETE | 작성자 | 리뷰 삭제 |
| `/api/v1/reviews/summary/{storeId}` | GET | 누구나 | AI 3줄 요약 |
| `/api/v1/uploads/review-image` | POST | 인증 | 리뷰 이미지 업로드 |
