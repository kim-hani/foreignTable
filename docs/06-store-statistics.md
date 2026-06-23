# 06. 식당 · 통계 · 즐겨찾기

> PostGIS 위치 기반 검색, JSONB 활용, 요일·시간별 혼잡도 통계 배치, 즐겨찾기

---

## 1. Store 엔티티 — PostGIS + JSONB의 집약체

```java
public class Store extends BaseEntity {
    private Long ownerId;            // 점주 회원 ID
    private String name;
    private String phone;
    private StoreCategory category;  // enum (한식/중식/...)

    @Builder.Default private Integer averageWaiting = 10;     // 팀당 평균 대기(분)
    @Builder.Default private Boolean isAcceptingWaiting = true; // 웨이팅 접수 on/off
    private Integer maxWaitingCount;  // 최대 대기 팀 수 (null = 무제한)

    @Builder.Default private Double averageRating = 0.0;  // 평균 별점 (05 문서)
    @Builder.Default private Integer reviewCount = 0;

    // [PostGIS] 위치 — WGS84(GPS) 좌표계
    @Column(columnDefinition = "geometry(point, 4326)")
    private Point location;

    // [JSONB] 영업시간 — {"mon": "10:00-22:00", "tue": "OFF"}
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> businessHours;

    // [JSONB] 메뉴 목록
    @JdbcTypeCode(SqlTypes.JSON)
    private List<MenuItemVo> menuItems;

    // [JSONB] 요일·시간별 혼잡도 통계 (배치가 갱신)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, List<HourlyStatVo>> weeklyWaitingStats;
}
```

### 왜 JSONB를 적극 활용하는가?
| 데이터 | JSONB 사용 이유 |
|---|---|
| `businessHours` | 요일×시간은 구조가 유동적. 별도 테이블 대신 한 컬럼에 유연 저장 |
| `menuItems` | 메뉴 목록을 정규화하면 조인 비용↑. JSONB로 한 번에 + `jsonb_path_exists`로 검색 가능 |
| `weeklyWaitingStats` | 7요일×24시간 = 168칸 통계. 정규화하면 테이블 비대 → JSON 한 덩어리로 관리 |

---

## 2. ⭐ PostGIS 위치 기반 검색

### 2.1 좌표 저장 — Coordinate(경도, 위도) 순서 고정

```java
GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
// ★ 순서 주의: (longitude, latitude) — PostGIS/WGS84 규격
Point location = geometryFactory.createPoint(
    new Coordinate(request.longitude(), request.latitude()));
```

> ⚠️ **`Coordinate(경도, 위도)` 순서는 절대 규칙**. PostGIS는 X=경도, Y=위도. 순서를 바꾸면 엉뚱한 위치로 저장됨. (SRID 4326 = WGS84 GPS 좌표계)

### 2.2 반경 검색 — ST_DWithin

`GET /api/v1/stores/nearby?lat=..&lng=..&radius=1000`

```sql
SELECT * FROM store s
WHERE ST_DWithin(
    s.location,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),  -- 내 위치 점 생성
    :radiusInMeters,
    false  -- 구면 기하학 미적용 (속도 우선)
)
```

| 함수 | 역할 |
|---|---|
| `ST_MakePoint(lng, lat)` | 입력 좌표로 점 생성 (경도, 위도 순) |
| `ST_SetSRID(.., 4326)` | 좌표계를 WGS84로 지정 |
| `ST_DWithin(A, B, R, false)` | A·B 거리가 R미터 이내인지 (false=구면계산 생략 → 빠름) |

**왜 DB에서 처리하나?**
모든 가게를 앱으로 가져와 거리 계산하면 비효율적. **PostGIS가 공간 인덱스로 DB에서 직접 필터링** → 빠르고 확장성 있음. 정밀 계산 필요 시 마지막 인자를 `true`로(use_spheroid).

### 2.3 이름·카테고리 검색

```java
@Query("SELECT s FROM Store s WHERE " +
       "(:name IS NULL OR s.name LIKE %:name%) AND " +
       "(:category IS NULL OR s.category = :category)")
Slice<Store> searchStoresByNameAndCategory(name, category, pageable);
```
→ name·category 둘 다 선택적(null이면 조건 무시). 페이징(Slice)으로 반환.

### 2.4 메뉴명 검색 (JSONB)

```sql
SELECT * FROM store s
WHERE jsonb_path_exists(s.menu_items, '$[*] ? (@.name like_regex :menuName flag "i")')
```
→ JSONB 메뉴 배열을 순회하며 메뉴명이 정규식과 매칭되는 가게 검색 (대소문자 무시).

---

## 3. 식당 관리 — StoreService

| 기능 | 엔드포인트 | 권한 | 설명 |
|---|---|---|---|
| 등록 | `POST /api/v1/stores` | OWNER | 위경도→Point 변환 후 저장 |
| 단건 조회 | `GET /api/v1/stores/{id}` | 누구나 | 평균 별점 포함 |
| 주변 검색 | `GET /api/v1/stores/nearby` | 누구나 | PostGIS 반경 검색 |
| 이름/카테고리 검색 | `GET /api/v1/stores/search` | 누구나 | 페이징 |
| 수정 | `PUT /api/v1/stores/{id}` | 점주 본인 | 위치 재계산 |
| 삭제 | `DELETE /api/v1/stores/{id}` | 점주/ADMIN | **soft delete** |
| 웨이팅 접수 on/off | `PATCH /api/v1/stores/{id}/waiting-status` | 점주 본인 | 일시 중단/재개 |

### 점주 권한 검증 패턴
```java
private void validateOwner(Store store, String email) {
    Member requester = memberRepository.findByEmail(email).orElseThrow(...);
    if (!store.getOwnerId().equals(requester.getId()))
        throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
}
```
→ 수정·삭제·접수상태 변경 모두 "내 가게인지" 확인 후 실행.

### 웨이팅 접수 일시 중단
```java
store.updateWaitingAcceptance(false);  // isAcceptingWaiting = false
```
→ 영업 마감/재료 소진 시 사장님이 신규 웨이팅을 막을 수 있음. 등록 시 [03-waiting](./03-waiting.md) §3.3 ③단계에서 체크.

---

## 4. ⭐ 요일·시간별 혼잡도 통계 — StoreStatisticService

"이 가게는 금요일 저녁 7시에 가장 붐빈다" 같은 **혼잡도 데이터**를 집계하는 기능. [03-waiting](./03-waiting.md) §9 야간 배치에서 호출됩니다.

### 4.1 집계 파이프라인

```
calculateAndSaveStoreStats(storeId)  (매일 새벽 4시 배치)
  │
  ① 7요일 × 24시간 = 168칸을 모두 0으로 초기화한 빈 맵 생성
  │   (데이터 없는 시간대도 0으로 표시하기 위함)
  │
  ② DB에서 최근 30일 통계를 GROUP BY로 직접 집계
  │   (자바가 아닌 PostgreSQL이 계산 → 성능)
  │
  ③ 집계 결과를 빈 맵의 해당 요일·시간 칸에 덮어쓰기
  │
  ▼
store.updateWeeklyStats(statsMap)  → JSONB로 저장 (더티 체킹)
```

### 4.2 DB 집계 쿼리 (네이티브)

```sql
SELECT
  CAST(EXTRACT(ISODOW FROM ticket_time) AS INTEGER) AS dayOfWeek,  -- 1(월)~7(일)
  CAST(EXTRACT(HOUR  FROM ticket_time) AS INTEGER) AS hourOfDay,   -- 0~23
  COUNT(*) / 4.0 AS avgTeams,           -- 4주 평균 팀 수
  AVG(expected_wait_min) AS avgWaitMin  -- 평균 대기 시간
FROM waiting
WHERE store_id = :storeId AND ticket_time >= :oneMonthAgo
GROUP BY EXTRACT(ISODOW FROM ticket_time), EXTRACT(HOUR FROM ticket_time)
```

| 결정 | 이유 |
|---|---|
| **DB에서 GROUP BY 집계** | 수천 건 웨이팅을 자바로 가져와 계산하면 느림. DB가 직접 요약 |
| **`COUNT(*) / 4.0`** | 30일 ≈ 4주 → 주당 평균 팀 수로 환산 |
| **빈 칸 0 채우기** | 데이터 없는 시간대도 "0팀"으로 명시 → 프론트가 168칸 그래프를 빈틈없이 렌더 |
| **`Projection` 인터페이스** | `WaitingStatProjection`으로 필요한 컬럼만 매핑 (DTO 최소화) |

### 4.3 결과 데이터 구조 (JSONB)

```json
{
  "MONDAY":  [{"hour":0,"avgTeams":0,"avgWaitMin":0}, ..., {"hour":19,"avgTeams":12,"avgWaitMin":35}],
  "FRIDAY":  [...],
  ...
}
```
→ `StoreResponseDto.weeklyWaitingStats`로 노출. 손님이 "한산한 시간대"를 골라 방문 가능.

---

## 5. 즐겨찾기 — FavoriteService

손님이 식당을 "찜"하는 기능.

### 5.1 Favorite 엔티티
- `Member` + `Store` 조합 (ManyToOne 각각)
- 회원당 같은 가게 중복 찜 불가

### 5.2 기능

| 기능 | 엔드포인트 | 검증 |
|---|---|---|
| 찜 추가 | `POST /api/v1/favorites` | 중복 시 `ALREADY_FAVORITE` |
| 찜 해제 | `DELETE /api/v1/favorites` | 없으면 `FAVORITE_NOT_FOUND` |
| 내 찜 목록 | `GET /api/v1/favorites/my` | 페이징(Slice) |

```java
@Transactional
public Long addFavorite(FavoriteRequestDto requestDto, String email) {
    Member member = memberRepository.findByEmail(email).orElseThrow(...);
    // 중복 체크
    if (favoriteRepository.existsByMemberIdAndStoreId(member.getId(), requestDto.storeId()))
        throw new BusinessException(ErrorCode.ALREADY_FAVORITE);
    Store store = storeRepository.findById(requestDto.storeId()).orElseThrow(...);
    return favoriteRepository.save(Favorite.builder().store(store).member(member).build()).getId();
}
```

---

## 6. 도메인 API 요약

### 식당
| 엔드포인트 | 메서드 | 권한 |
|---|---|---|
| `/api/v1/stores` | POST | OWNER |
| `/api/v1/stores/{id}` | GET | 누구나 |
| `/api/v1/stores/nearby` | GET | 누구나 |
| `/api/v1/stores/search` | GET | 누구나 |
| `/api/v1/stores/{id}` | PUT | 점주 |
| `/api/v1/stores/{id}` | DELETE | 점주/ADMIN |
| `/api/v1/stores/{id}/waiting-status` | PATCH | 점주 |

### 즐겨찾기
| 엔드포인트 | 메서드 | 권한 |
|---|---|---|
| `/api/v1/favorites` | POST | USER |
| `/api/v1/favorites` | DELETE | USER |
| `/api/v1/favorites/my` | GET | USER |

---

## 7. 데이터 저장 기술 종합

이 도메인에서 PostgreSQL의 확장 기능을 어떻게 쓰는지 한눈에:

```
┌─────────────────────────────────────────────────────────┐
│                     Store 테이블                          │
├──────────────┬──────────────────────────────────────────┤
│ location      │ PostGIS Point  → ST_DWithin 반경 검색      │
│ business_hours│ JSONB          → 유연한 영업시간            │
│ menu_items    │ JSONB          → jsonb_path_exists 메뉴검색 │
│ weekly_stats  │ JSONB          → 168칸 혼잡도 통계          │
│ average_rating│ Double         → 리뷰 집계 (05 문서)        │
└──────────────┴──────────────────────────────────────────┘
```

→ **하나의 PostgreSQL**로 관계형 + 공간(GIS) + 문서(JSON) 데이터를 모두 처리. 별도 NoSQL·검색엔진 없이 단일 DB로 요구사항 충족.
