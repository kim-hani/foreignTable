package OneTwo.SmartWaiting.domain.waiting.event;

/**
 * 대기열에서 한 팀이 빠지거나 뒤로 밀려 순번이 당겨졌음을 알린다.
 * 커밋 후 새 대기열을 다시 조회해 새로 3번째·1번째가 된 손님에게 알림을 보낸다.
 *
 * @param notifyThird 빠진 팀이 3번째 이내였는지 (새 3번째가 생김)
 * @param notifyFirst 빠진 팀이 1번째였는지 (새 1번째가 생김)
 */
public record WaitingQueueChangedEvent(Long storeId, boolean notifyThird, boolean notifyFirst) {
}
