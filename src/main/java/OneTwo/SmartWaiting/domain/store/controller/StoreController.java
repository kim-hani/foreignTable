package OneTwo.SmartWaiting.domain.store.controller;

import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreCreateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreUpdateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreResponseDto;
import OneTwo.SmartWaiting.domain.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    // 1. 식당 등록
    @PostMapping
    public ResponseEntity<Long> createStore(
            Principal principal,
            @RequestBody @Valid StoreCreateRequestDto request) {
        Long storeId = storeService.createStore(request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/stores/" + storeId)).body(storeId);
    }

    // 2. 식당 단건 조회
    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponseDto> getStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeService.getStore(storeId));
    }

    // 3. 내 주변 식당 검색
    @GetMapping("/nearby")
    public ResponseEntity<List<StoreResponseDto>> searchNearbyStores(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") double radius) {

        return ResponseEntity.ok(storeService.searchStoresAround(lat, lng, radius));
    }

    @PutMapping("/{storeId}")
    public ResponseEntity<Void> updateStore(
            @PathVariable Long storeId,
            Principal principal,
            @RequestBody @Valid StoreUpdateRequestDto request) {
        storeService.updateStore(storeId, principal.getName(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{storeId}")
    public ResponseEntity<Void> deleteStore(
            @PathVariable Long storeId,
            Principal principal) {
        storeService.deleteStore(storeId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
