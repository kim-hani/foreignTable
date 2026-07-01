package OneTwo.SmartWaiting.domain.support.dto;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SupportManualResponseDto(
        Long id,
        String category,
        String question,
        String answer,
        String keywords,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupportManualResponseDto from(SupportManual manual) {
        return SupportManualResponseDto.builder()
                .id(manual.getId())
                .category(manual.getCategory())
                .question(manual.getQuestion())
                .answer(manual.getAnswer())
                .keywords(manual.getKeywords())
                .createdAt(manual.getCreatedAt())
                .updatedAt(manual.getUpdatedAt())
                .build();
    }
}
