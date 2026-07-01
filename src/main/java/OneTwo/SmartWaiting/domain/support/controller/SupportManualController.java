package OneTwo.SmartWaiting.domain.support.controller;

import OneTwo.SmartWaiting.domain.support.dto.SupportManualCreateRequestDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualResponseDto;
import OneTwo.SmartWaiting.domain.support.dto.SupportManualUpdateRequestDto;
import OneTwo.SmartWaiting.domain.support.service.SupportManualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "고객지원 매뉴얼(Support Manual) API", description = "운영자(ADMIN)가 AI 챗봇의 지식 출처가 되는 고객지원 매뉴얼을 관리합니다.")
@RestController
@RequestMapping("/api/v1/admin/support-manuals")
@RequiredArgsConstructor
public class SupportManualController {

    private final SupportManualService supportManualService;

    @Operation(summary = "매뉴얼 등록", description = "운영자(ADMIN)가 고객지원 매뉴얼을 등록합니다.")
    @PostMapping
    public ResponseEntity<Long> create(@RequestBody @Valid SupportManualCreateRequestDto request) {
        Long id = supportManualService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/support-manuals/" + id)).body(id);
    }

    @Operation(summary = "매뉴얼 목록 조회", description = "고객지원 매뉴얼 목록을 조회합니다. category 파라미터로 필터링할 수 있습니다.")
    @GetMapping
    public ResponseEntity<List<SupportManualResponseDto>> getAll(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(supportManualService.getAll(category));
    }

    @Operation(summary = "매뉴얼 수정", description = "운영자(ADMIN)가 기존 매뉴얼을 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @RequestBody @Valid SupportManualUpdateRequestDto request) {
        supportManualService.update(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "매뉴얼 삭제", description = "운영자(ADMIN)가 매뉴얼을 삭제 처리합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supportManualService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
