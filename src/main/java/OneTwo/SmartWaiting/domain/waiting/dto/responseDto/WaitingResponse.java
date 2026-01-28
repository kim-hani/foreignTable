package OneTwo.SmartWaiting.domain.waiting.dto.responseDto;

import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WaitingResponse(
        Long waitingId,
        Long storeId,
        String storeName,
        Long memberId,
        String memberName,
        Integer headCount,
        WaitingStatus status,
        Integer queueNumber,
        Integer expectedWaitMin,
        LocalDateTime createdAt
) {
    public static WaitingResponse from(Waiting waiting){
        return WaitingResponse.builder()
                .waitingId(waiting.getId())
                .storeId(waiting.getStore().getId())
                .storeName(waiting.getStore().getName())
                .memberId(waiting.getMember().getId())
                .memberName(waiting.getMember().getNickname())
                .headCount(waiting.getHeadCount())
                .status(waiting.getStatus())
                .queueNumber(waiting.getQueueNumber())
                .expectedWaitMin(waiting.getExpectedWaitMin())
                .createdAt(LocalDateTime.ofInstant(waiting.getCreatedAt(), java.time.ZoneId.systemDefault()))
                .build();
    }
}
