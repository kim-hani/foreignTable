package OneTwo.SmartWaiting.domain.store.repository;

import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    /**
     * [PostGIS] 내 위치 기준 반경 N미터 이내 식당 조회 (가까운 순)
     *
     * @param lat            내 위도 (Latitude)
     * @param lng            내 경도 (Longitude)
     * @param radiusInMeters 검색 반경 (미터 단위)
     * @return 반경 내 식당 리스트 (삭제된 식당 제외, 거리 오름차순)
     * <p>
     * 기술 설명:
     * 1. ST_MakePoint(lng, lat): 입력받은 좌표로 점을 생성 (순서 주의: 경도, 위도)
     * 2. ST_SetSRID(..., 4326): 해당 점의 좌표계를 WGS84(GPS)로 설정
     * 3. ::geography — 미터 단위 거리 계산을 위해 geography로 변환. location 컬럼은 geometry라 geography 전용인
     *    ST_DWithin(..., false)를 호출하면 location이 geography로 변환되므로, location 컬럼에 일반 GIST 인덱스를 걸어도 쓰지 못한다.
     *    같은 식의 표현식 인덱스 idx_store_location_geog(GIST, (location::geography))를 사용하며, 변환을 명시해 인덱스 식과 맞춘다.
     * 4. ST_DWithin(A, B, R, false): A와 B 사이의 거리가 R 미터 이내인지 확인 (구면 계산 -> 속도 위주)
     */
    @Query(value = """
            SELECT * FROM store s
            WHERE s.is_deleted = false
              AND ST_DWithin(
                s.location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusInMeters,
                false
              )
            ORDER BY ST_Distance(s.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, false)
            """, nativeQuery = true)
    List<Store> findStoresWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusInMeters") double radiusInMeters
    );

    /**
     * [JSONB] 메뉴 이름으로 식당 검색
     *
     * @param menuName 검색할 메뉴 이름 (ex: "삼겹살")
     * @return 해당 메뉴를 포함하는 식당 리스트
     * <p>
     * 기술 설명:
     * 1. jsonb_path_exists: JSONB 컬럼 내부를 탐색하는 함수
     * 2. '$[*]': JSON 배열의 모든 요소를 순회
     * 3. '? (@.name like_regex ...)': 각 요소의 name 필드가 정규식 조건에 맞는지 검사
     * 4. flag "i": 대소문자 구분 없음 (Case Insensitive)
     */
    @Query(value = """
            SELECT * FROM store s 
            WHERE jsonb_path_exists(s.menu_items, '$[*] ? (@.name like_regex :menuName flag "i")')
            """, nativeQuery = true)
    List<Store> findByMenuName(@Param("menuName") String menuName);

    // [추가] 점주 ID로 가게 목록 조회 (나의 가게 관리용)
    List<Store> findByOwnerId(Long ownerId);

    // 이름/카테고리 검색 — 조건 조합마다 쿼리를 분리해 삭제된 식당을 제외하고, OR 조건 없는 단순한 실행 계획을 만든다.
    // Containing은 입력값의 %, _를 이스케이프하므로 사용자가 와일드카드를 주입할 수 없다.
    // 이름은 LIKE '%키워드%'(앞에 %)라 B-tree가 아닌 idx_store_name_trgm(GIN, pg_trgm)을 사용한다.
    Slice<Store> findByIsDeletedFalseAndNameContainingAndCategory(String name, StoreCategory category, Pageable pageable);

    Slice<Store> findByIsDeletedFalseAndNameContaining(String name, Pageable pageable);

    Slice<Store> findByIsDeletedFalseAndCategory(StoreCategory category, Pageable pageable);

    Slice<Store> findByIsDeletedFalse(Pageable pageable);
}

