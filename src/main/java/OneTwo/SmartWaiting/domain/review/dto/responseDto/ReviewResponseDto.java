package OneTwo.SmartWaiting.domain.review.dto.responseDto;

import OneTwo.SmartWaiting.domain.review.entity.Review;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ReviewResponseDto(
        Long reviewId,
        String content,
        int rating,
        String writerNickname,
        LocalDateTime createdAt,
        List<String> imageUrls
) {
    public static ReviewResponseDto from(Review review) {
        return ReviewResponseDto.builder()
                .reviewId(review.getId())
                .content(review.getContent())
                .rating(review.getRating())
                .writerNickname(review.getMember().getNickname())
                .createdAt(review.getCreatedAt())
                .imageUrls(review.getImageUrls())
                .build();
    }
}
