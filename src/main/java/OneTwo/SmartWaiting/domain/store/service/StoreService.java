package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreCreateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreUpdateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreResponseDto;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    // 식당 생성
    @Transactional
    public Long createStore(StoreCreateRequestDto request,String email) {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(member.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point location = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));

        Store store = Store.builder()
                .ownerId(member.getId())
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
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        return StoreResponseDto.from(store);
    }

    // [PostGIS] 반경 검색
    public List<StoreResponseDto> searchStoresAround(double lat, double lng, double radiusInMeters) {
        return storeRepository.findStoresWithinRadius(lat, lng, radiusInMeters)
                .stream()
                .map(StoreResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateStore(Long storeId, String email, StoreUpdateRequestDto request) {
        Store store = findStoreOrThrow(storeId);
        validateOwner(store, email);

        // 수정된 위경도를 바탕으로 다시 Point 객체 생성
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point updatedLocation = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));

        store.updateInfo(
                request.name(),
                request.phone(),
                StoreCategory.from(request.category()),
                request.averageWaiting(),
                updatedLocation, // 위치 정보 전달
                request.businessHours(),
                request.menuItems()
        );
    }

    @Transactional
    public void deleteStore(Long storeId, String email) {
        Store store = findStoreOrThrow(storeId);
        validateOwner(store, email);

        store.softDelete();
    }

    private Store findStoreOrThrow(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void validateOwner(Store store, String email) {
        Member requester = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(!store.getOwnerId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
        }
    }

    public Slice<StoreResponseDto> searchStores(String name, String categoryStr, Pageable pageable){
        StoreCategory category = (categoryStr != null && !categoryStr.isBlank()) ? StoreCategory.from(categoryStr) : null;

        return storeRepository.searchStoresByNameAndCategory(name,category,pageable)
                .map(StoreResponseDto::from);
    }
}
