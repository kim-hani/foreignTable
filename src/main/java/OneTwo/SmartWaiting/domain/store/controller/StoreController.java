package OneTwo.SmartWaiting.domain.store.controller;

import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreCreateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreUpdateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreResponseDto;
import OneTwo.SmartWaiting.domain.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(name = "가게(Store) API", description = "식당 등록, 조회, 수정, 삭제 기능")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    // 1. 식당 등록
    @Operation(summary = "식당 등록", description = "사장님(OWNER) 권한으로 새로운 식당을 등록합니다.")
    @PostMapping
    public ResponseEntity<Long> createStore(
            Principal principal,
            @RequestBody @Valid StoreCreateRequestDto request) {
        Long storeId = storeService.createStore(request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/stores/" + storeId)).body(storeId);
    }

    // 2. 식당 단건 조회
    @Operation(summary = "식당 단건 조회", description = "가게 ID를 통해 특정 식당의 상세 정보를 조회합니다.")
    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponseDto> getStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeService.getStore(storeId));
    }

    // 3. 내 주변 식당 검색
    @Operation(summary = "내 주변 식당 검색", description = "주어진 위도/경도를 기준으로 반경 내의 식당을 검색합니다.")
    @GetMapping("/nearby")
    public ResponseEntity<List<StoreResponseDto>> searchNearbyStores(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") double radius) {

        return ResponseEntity.ok(storeService.searchStoresAround(lat, lng, radius));
    }

    @Operation(summary = "식당 정보 수정", description = "본인의 식당 정보를 수정합니다.")
    @PutMapping("/{storeId}")
    public ResponseEntity<Void> updateStore(
            @PathVariable Long storeId,
            Principal principal,
            @RequestBody @Valid StoreUpdateRequestDto request) {
        storeService.updateStore(storeId, principal.getName(), request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "식당 삭제", description = "본인의 식당을 삭제 처리합니다.")
    @DeleteMapping("/{storeId}")
    public ResponseEntity<Void> deleteStore(
            @PathVariable Long storeId,
            Principal principal) {
        storeService.deleteStore(storeId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
