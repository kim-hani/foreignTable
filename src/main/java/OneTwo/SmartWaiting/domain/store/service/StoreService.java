package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreCreateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreResponseDto;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // 식당 생성
    @Transactional
    public Long createStore(StoreCreateRequestDto request) {

        Member member = memberRepository.findById(request.ownerId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if(member.getRole() != UserRole.OWNER) {
            throw new IllegalArgumentException("식당 등록 권한이 없습니다.");
        }

        Point location = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));

        Store store = Store.builder()
                .ownerId(request.ownerId())
                .name(request.name())
                .category(request.category())
                .averageWaiting(request.averageWaiting() != null ? request.averageWaiting() : 10)
                .phone(request.phone())
                .location(location)
                .businessHours(request.businessHours())
                .menuItems(request.menuItems())
                .build();

        return storeRepository.save(store).getId();
    }

    // 식당 단건 조회
    public StoreResponseDto getStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다. ID=" + storeId));

        return StoreResponseDto.from(store);
    }

    // [PostGIS] 반경 검색
    public List<StoreResponseDto> searchStoresAround(double lat, double lng, double radiusInMeters) {
        return storeRepository.findStoresWithinRadius(lat, lng, radiusInMeters)
                .stream()
                .map(StoreResponseDto::from)
                .collect(Collectors.toList());
    }
}
