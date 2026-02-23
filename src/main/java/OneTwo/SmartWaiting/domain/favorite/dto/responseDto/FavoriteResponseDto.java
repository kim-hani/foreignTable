package OneTwo.SmartWaiting.domain.favorite.dto.responseDto;

import OneTwo.SmartWaiting.domain.favorite.entity.Favorite;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import lombok.Builder;

@Builder
public record FavoriteResponseDto(
        Long favoriteId,
        Long storeId,
        String storeName,
        StoreCategory storeCategory,
        String storePhone
) {
    public static FavoriteResponseDto from(Favorite favorite) {
        return FavoriteResponseDto.builder()
                .favoriteId(favorite.getId())
                .storeId(favorite.getStore().getId())
                .storeName(favorite.getStore().getName())
                .storeCategory(favorite.getStore().getCategory())
                .storePhone(favorite.getStore().getPhone())
                .build();
    }
}
