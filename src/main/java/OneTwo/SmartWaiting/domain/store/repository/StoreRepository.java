package OneTwo.SmartWaiting.domain.store.repository;

import OneTwo.SmartWaiting.domain.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    /**
     * [PostGIS] 내 위치 기준 반경 N미터 이내 식당 조회
     *
     * @param lat            내 위도 (Latitude)
     * @param lng            내 경도 (Longitude)
     * @param radiusInMeters 검색 반경 (미터 단위)
     * @return 반경 내 식당 리스트
     * <p>
     * 기술 설명:
     * 1. ST_MakePoint(lng, lat): 입력받은 좌표로 점을 생성 (순서 주의: 경도, 위도)
     * 2. ST_SetSRID(..., 4326): 해당 점의 좌표계를 WGS84(GPS)로 설정
     * 3. ST_DWithin(A, B, R, false): A와 B 사이의 거리가 R 미터 이내인지 확인 (구면 기하학 적용 안 함 -> 속도 위주)
     * * 정밀한 계산이 필요하면 마지막 인자를 true로 변경(use_spheroid)하거나 ST_DistanceSphere 사용
     */
    @Query(value = """
            SELECT * FROM store s 
            WHERE ST_DWithin(
                s.location, 
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 
                :radiusInMeters, 
                false
            )
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
}

