package OneTwo.SmartWaiting.domain.store.dto.requestDto;

import OneTwo.SmartWaiting.domain.store.entity.MenuItemVo;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record StoreCreateRequestDto(
        @NotNull(message = "점주 ID는 필수입니다.")
        Long ownerId,

        @NotBlank(message = "식당 이름은 필수입니다.")
        String name,

        @NotNull(message = "카테고리는 필수입니다.")
        StoreCategory category,

        String phone,

        @NotNull(message = "위도는 필수입니다.")
        @Min(value = -90, message = "위도는 -90 이상이어야 합니다.")
        @Max(value = 90, message = "위도는 90 이하이어야 합니다.")
        Double latitude, // 위도 (ex: 37.5665)

        @NotNull(message = "경도는 필수입니다.")
        @Min(value = -180, message = "경도는 -180 이상이어야 합니다.")
        @Max(value = 180, message = "경도는 180 이하이어야 합니다.")
        Double longitude, // 경도 (ex: 126.9780)

        Integer averageWaiting,

        // JSONB 데이터
        Map<String, String> businessHours,
        List<MenuItemVo> menuItems
) {}
