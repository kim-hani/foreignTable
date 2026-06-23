package OneTwo.SmartWaiting.domain.waiting.dto.responseDto;

import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;

public record WaitingStatusResponse(
        WaitingStatus status,
        int currentRank,
        long teamsAhead,
        int expectedWaitMin
) {}
