package OneTwo.SmartWaiting.domain.store.dto.responseDto;

import OneTwo.SmartWaiting.domain.store.entity.HourlyStatVo;
import OneTwo.SmartWaiting.domain.store.entity.MenuItemVo;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;

import java.util.List;
import java.util.Map;

public record StoreResponseDto(
        Long storeId,
        String name,
        StoreCategory category,
        String phone,
        Double latitude,
        Double longitude,
        Map<String, String> businessHours,
        List<MenuItemVo> menuItems,
        Map<String,List<HourlyStatVo>> weeklyWaitingStats,
        Boolean isAcceptingWaiting
) {
    public static StoreResponseDto from(Store store) {
        return new StoreResponseDto(
                store.getId(),
                store.getName(),
                store.getCategory(),
                store.getPhone(),
                store.getLocation().getY(),
                store.getLocation().getX(),
                store.getBusinessHours(),
                store.getMenuItems(),
                store.getWeeklyWaitingStats(),
                store.getIsAcceptingWaiting()
        );
    }
}
