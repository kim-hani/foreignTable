package OneTwo.SmartWaiting.domain.store.dto.responseDto;

import OneTwo.SmartWaiting.domain.store.entity.MenuItemVo;
import OneTwo.SmartWaiting.domain.store.entity.Store;

import java.util.List;
import java.util.Map;

public record StoreResponseDto(
        Long storeId,
        String name,
        String category,
        String phone,
        Double latitude,
        Double longitude,
        Map<String, String> businessHours,
        List<MenuItemVo> menuItems
) {
    // Entity -> DTO 변환을 위한 정적 팩토리 메서드 (추천 패턴)
    public static StoreResponseDto from(Store store) {
        return new StoreResponseDto(
                store.getId(),
                store.getName(),
                store.getCategory(),
                store.getPhone(), // Store 엔티티에 phone 필드가 있다고 가정
                store.getLocation().getY(), // 위도 (Latitude)
                store.getLocation().getX(), // 경도 (Longitude)
                store.getBusinessHours(),
                store.getMenuItems()
        );
    }
}
