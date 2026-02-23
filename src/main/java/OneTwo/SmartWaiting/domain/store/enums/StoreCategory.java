package OneTwo.SmartWaiting.domain.store.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum StoreCategory {

    KOREAN("한식"),
    CHINESE("중식"),
    JAPANESE("일식"),
    WESTERN("양식"),
    CAFE("카페/디저트"),
    ETC("기타");

    private final String description;

    public static StoreCategory from(String category) {
        return Arrays.stream(StoreCategory.values())
                .filter(c -> c.name().equalsIgnoreCase(category) || c.description.equals(category))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리입니다: " + category));
    }
}
