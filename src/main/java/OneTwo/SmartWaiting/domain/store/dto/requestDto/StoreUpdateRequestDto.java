package OneTwo.SmartWaiting.domain.store.dto.requestDto;


import OneTwo.SmartWaiting.domain.store.entity.MenuItemVo;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record StoreUpdateRequestDto(
        @NotBlank(message = "식당 이름은 필수입니다.")
        String name,

        @NotBlank(message = "카테고리는 필수입니다.")
        String category,

        String phone,

        Integer averageWaiting,

        // [추가됨] 위치 정보 수정을 위한 위도/경도
        @NotNull(message = "위도는 필수입니다.")
        @Min(value = -90, message = "위도는 -90 이상이어야 합니다.")
        @Max(value = 90, message = "위도는 90 이하이어야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @Min(value = -180, message = "경도는 -180 이상이어야 합니다.")
        @Max(value = 180, message = "경도는 180 이하이어야 합니다.")
        Double longitude,

        Map<String, String> businessHours,
        List<MenuItemVo> menuItems
) {}
