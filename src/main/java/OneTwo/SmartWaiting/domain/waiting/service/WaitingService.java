package OneTwo.SmartWaiting.domain.waiting.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingStatusResponse;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingConnectionCloseEvent;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingMessageEvent;
import OneTwo.SmartWaiting.domain.waiting.event.WaitingQueueChangedEvent;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALL);

    // 대기 번호 발급의 영업일 경계 — 새벽 4시 정리 배치(CleanupScheduler)가 남은 대기를 모두 종료하므로 같은 시각을 사용
    static final LocalTime BUSINESS_DAY_START = LocalTime.of(4, 0);

    private final WaitingRepository waitingRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    // 알림은 직접 보내지 않고 이벤트로 발행 → 커밋 후 WaitingNotificationListener가 발송
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!store.getIsAcceptingWaiting()) {
            throw new BusinessException(ErrorCode.STORE_NOT_ACCEPTING_WAITING);
        }

        // 내 앞에 남아 있는 팀 수 (대기 + 호출)
        long teamsAhead = waitingRepository.countByStoreIdAndStatusIn(store.getId(), ACTIVE_STATUSES);

        if (store.getMaxWaitingCount() != null && teamsAhead >= store.getMaxWaitingCount()) {
            throw new BusinessException(ErrorCode.WAITING_QUEUE_FULL);
        }

        if(member.isBlacklisted()){
            throw new BusinessException(ErrorCode.BLACKLISTED_MEMBER);
        }

        // 호출(CALL)된 상태에서도 같은 가게에 중복 등록할 수 없음
        if (waitingRepository.existsByMemberIdAndStoreIdAndStatusIn(member.getId(), store.getId(), ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_EXISTS);
        }

        // 대기 번호는 "남은 팀 수 + 1"이 아니라 영업일 내 마지막 발급 번호 + 1
        // (남은 팀 수 기준이면 앞 팀이 입장·취소한 뒤 이미 발급된 번호가 다시 발급됨). 분산락 안에서 실행되므로 동시 발급 중복 없음
        LocalDateTime now = LocalDateTime.now();
        int myQueueNumber = waitingRepository.findMaxQueueNumberSince(store.getId(), businessDayStart(now)) + 1;
        int expectedWaitMin = (int) teamsAhead * store.getAverageWaiting();

        Waiting waiting = Waiting.builder()
                .store(store)
                .member(member)
                .headCount(requestDto.headCount())
                .status(WaitingStatus.WAITING)
                .queueNumber(myQueueNumber)
                .expectedWaitMin(expectedWaitMin)
                .postponedCount(0)
                .ticketTime(now)
                .build();

        waiting = waitingRepository.save(waiting);

        // 순번(앞 팀 수 기준)이 3번째일 때 안내 — 대기 번호는 순번과 다를 수 있음
        if (teamsAhead == 2) {
            eventPublisher.publishEvent(new WaitingMessageEvent(member.getId(), "📢 대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요."));
        }

        return waiting.getId();
    }

    @Transactional
    public void cancelWaiting(Long waitingId, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!waiting.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);
        }

        boolean shouldNotify = isTop3(waiting);
        boolean shouldNotifyFirst = isTop2(waiting);

        waiting.changeStatus(WaitingStatus.CANCEL);

        publishQueueChanged(waiting.getStore().getId(), shouldNotify, shouldNotifyFirst);
        eventPublisher.publishEvent(new WaitingConnectionCloseEvent(waiting.getMember().getId()));
    }

    public List<WaitingResponse> getMyWaitings(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return waitingRepository.findAllByMemberId(member.getId()).stream()
                .map(waiting -> {
                    Long teamsAhead = 0L;
                    Integer expectedWaitMin = 0;

                    if (waiting.getStatus() == WaitingStatus.WAITING || waiting.getStatus() == WaitingStatus.CALL) {
                        teamsAhead = waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(
                                waiting.getStore().getId(),
                                Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL),
                                waiting.getTicketTime()
                        );
                        expectedWaitMin = (int) (teamsAhead * waiting.getStore().getAverageWaiting());
                    }

                    return WaitingResponse.of(waiting, teamsAhead, expectedWaitMin);
                })
                .collect(Collectors.toList());
    }

    public List<WaitingResponse> getStoreWaitings(Long storeId, WaitingStatus status, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        if (!store.getOwnerId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
        }

        return waitingRepository.findAllByStoreIdAndStatusOrderByTicketTimeAsc(storeId, status).stream()
                .map(waiting -> WaitingResponse.of(waiting, 0L, 0))
                .collect(Collectors.toList());
    }

    @Transactional
    public void changeStatus(Long waitingId, WaitingChangeRequestDto request, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!waiting.getStore().getOwnerId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
        }

        boolean shouldNotify = isTop3(waiting);
        boolean shouldNotifyFirst = isTop2(waiting);

        waiting.changeStatus(request.status());

        if(request.status() == WaitingStatus.NOSHOW){
            waiting.getMember().incrementNoShowCount();
        }

        switch (request.status()) {
            case CALL:
                eventPublisher.publishEvent(new WaitingMessageEvent(waiting.getMember().getId(), "📢 사장님이 호출하셨습니다! 매장으로 입장해 주세요."));
                break;
            case SEATED:
            case CANCEL:
            case NOSHOW:
                publishQueueChanged(waiting.getStore().getId(), shouldNotify, shouldNotifyFirst);
                eventPublisher.publishEvent(new WaitingConnectionCloseEvent(waiting.getMember().getId()));
                break;
            default:
                break;
        }
    }

    @Transactional
    public void postponeWaiting(Long waitingId, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!waiting.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);
        }

        if (waiting.getStatus() != WaitingStatus.WAITING) {
            throw new BusinessException(ErrorCode.INVALID_WAITING_STATUS);
        }

        boolean shouldNotify = isTop3(waiting);
        boolean shouldNotifyFirst = isTop2(waiting);

        waiting.postpone();

        publishQueueChanged(waiting.getStore().getId(), shouldNotify, shouldNotifyFirst);
    }

    public WaitingStatusResponse getWaitingStatus(Long waitingId, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!waiting.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);
        }

        long teamsAhead = 0L;
        int expectedWaitMin = 0;

        if (waiting.getStatus() == WaitingStatus.WAITING || waiting.getStatus() == WaitingStatus.CALL) {
            teamsAhead = waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(
                    waiting.getStore().getId(),
                    Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL),
                    waiting.getTicketTime()
            );
            expectedWaitMin = (int) (teamsAhead * waiting.getStore().getAverageWaiting());
        }

        int currentRank = (waiting.getStatus() == WaitingStatus.WAITING || waiting.getStatus() == WaitingStatus.CALL)
                ? (int) teamsAhead + 1
                : 0;

        return new WaitingStatusResponse(waiting.getStatus(), currentRank, teamsAhead, expectedWaitMin);
    }

    static LocalDateTime businessDayStart(LocalDateTime now) {
        LocalDateTime todayStart = now.toLocalDate().atTime(BUSINESS_DAY_START);
        return now.isBefore(todayStart) ? todayStart.minusDays(1) : todayStart;
    }

    boolean isTop2(Waiting waiting) {
        if (waiting.getStatus() != WaitingStatus.WAITING && waiting.getStatus() != WaitingStatus.CALL) {
            return false;
        }
        long teamsAhead = waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(
                waiting.getStore().getId(),
                Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL),
                waiting.getTicketTime()
        );
        // 내가 정확히 1등일 때만 true — 내가 빠져야 새로운 1등이 생기는 경우
        return teamsAhead == 0;
    }

    boolean isTop3(Waiting waiting) {
        if (waiting.getStatus() != WaitingStatus.WAITING && waiting.getStatus() != WaitingStatus.CALL) {
            return false; // 이미 취소/노쇼/착석 된 상태면 순위를 계산할 필요 없음
        }

        // 내 티켓 시간보다 먼저 발권한 'WAITING' 상태의 사람 수를 센다 (= 내 앞에 대기 중인 팀 수)
        long teamsAhead = waitingRepository.countByStoreIdAndStatusInAndTicketTimeLessThan(
                waiting.getStore().getId(),
                Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL),
                waiting.getTicketTime()
        );

        // 내 앞의 대기팀이 0팀(내가 1등), 1팀(내가 2등), 2팀(내가 3등)인 경우에만 true 반환
        return teamsAhead < 3;
    }

    // 새 3번째·1번째 조회와 알림은 커밋된 대기열 기준으로 해야 하므로 커밋 후 리스너에서 수행
    private void publishQueueChanged(Long storeId, boolean notifyThird, boolean notifyFirst) {
        if (notifyThird || notifyFirst) {
            eventPublisher.publishEvent(new WaitingQueueChangedEvent(storeId, notifyThird, notifyFirst));
        }
    }
}