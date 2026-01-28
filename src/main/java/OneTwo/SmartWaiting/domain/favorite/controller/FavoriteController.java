package OneTwo.SmartWaiting.domain.favorite.controller;

import OneTwo.SmartWaiting.domain.favorite.dto.requestDto.FavoriteRequestDto;
import OneTwo.SmartWaiting.domain.favorite.dto.responseDto.FavoriteResponseDto;
import OneTwo.SmartWaiting.domain.favorite.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<Long> addFavorite(@RequestBody @Valid FavoriteRequestDto requestDto){
        Long favoriteId = favoriteService.addFavorite(requestDto);
        return ResponseEntity.ok(favoriteId);
    }

    @DeleteMapping
    public ResponseEntity<Void> removeFavorite(@RequestBody @Valid FavoriteRequestDto requestDto){
        favoriteService.removeFavorite(requestDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<FavoriteResponseDto>> getMyFavorites(@RequestParam Long memberId) {
        return ResponseEntity.ok(favoriteService.getMyFavorites(memberId));
    }
}
