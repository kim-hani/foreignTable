package OneTwo.SmartWaiting.domain.favorite.controller;

import OneTwo.SmartWaiting.domain.favorite.dto.requestDto.FavoriteRequestDto;
import OneTwo.SmartWaiting.domain.favorite.dto.responseDto.FavoriteResponseDto;
import OneTwo.SmartWaiting.domain.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Tag(name = "6. 즐겨찾기(Favorite) API", description = "식당 찜하기, 찜 해제 및 목록 조회 기능")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "즐겨찾기 추가 (찜하기)", description = "특정 식당을 내 즐겨찾기에 추가합니다.")
    @PostMapping
    public ResponseEntity<Long> addFavorite(
            Principal principal,
            @RequestBody @Valid FavoriteRequestDto requestDto){
        Long favoriteId = favoriteService.addFavorite(requestDto,principal.getName());
        return ResponseEntity.ok(favoriteId);
    }

    @Operation(summary = "즐겨찾기 취소 (찜 해제)", description = "특정 식당의 즐겨찾기를 해제합니다.")
    @DeleteMapping
    public ResponseEntity<Void> removeFavorite(
            Principal principal,
            @RequestBody @Valid FavoriteRequestDto requestDto){
        favoriteService.removeFavorite(requestDto,principal.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 즐겨찾기 목록 조회", description = "본인이 찜한 식당 목록을 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<List<FavoriteResponseDto>> getMyFavorites(Principal principal) {
        return ResponseEntity.ok(favoriteService.getMyFavorites(principal.getName()));
    }
}
