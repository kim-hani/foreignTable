package OneTwo.SmartWaiting.domain.waiting.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingChangeRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.dto.responseDto.WaitingResponse;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

    private final WaitingRepository waitingRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(member.isBlacklisted()){
            throw new BusinessException(ErrorCode.BLACKLISTED_MEMBER);
        }

        if (waitingRepository.existsByMemberIdAndStoreIdAndStatus(member.getId(), store.getId(), WaitingStatus.WAITING)) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_EXISTS);
        }

        long currentWaitingCount = waitingRepository.countByStoreIdAndStatusIn(
                store.getId(), Arrays.asList(WaitingStatus.WAITING, WaitingStatus.CALL));
        int myQueueNumber = (int) currentWaitingCount + 1;
        int expectedWaitMin = myQueueNumber * store.getAverageWaiting();

        Waiting waiting = Waiting.builder()
                .store(store)
                .member(member)
                .headCount(requestDto.headCount())
                .status(WaitingStatus.WAITING)
                .queueNumber(myQueueNumber)
                .expectedWaitMin(expectedWaitMin)
                .postponedCount(0)
                .ticketTime(LocalDateTime.now())
                .build();

        waiting = waitingRepository.save(waiting);

        if (myQueueNumber == 3) {
            notificationService.sendToClient(member.getId(), "📢 대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요.");
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

        // 1. 상태를 변경하기 전에 내가 상위 3등 이내였는지 미리 확인
        boolean shouldNotify = isTop3(waiting);

        // 2. 상태 변경
        waiting.changeStatus(WaitingStatus.CANCEL);

        waitingRepository.flush();

        // 3. 내가 상위 3등 이내여서 줄의 변동이 생겼을 때만 3번째 사람에게 알림 전송
        if (shouldNotify) {
            checkAndNotifyThirdInLine(waiting.getStore().getId());
        }

        notificationService.closeConnection(waiting.getMember().getId());
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

        // 1. 상태 변경 전 상위 3등 이내인지 확인
        boolean shouldNotify = isTop3(waiting);

        // 2. 상태 변경
        waiting.changeStatus(request.status());

        if(request.status() == WaitingStatus.NOSHOW){
            waiting.getMember().incrementNoShowCount();
        }

        waitingRepository.flush();

        // 3. 조건에 맞는 알림 전송
        switch (request.status()) {
            case CALL:
                notificationService.sendToClient(waiting.getMember().getId(), "📢 사장님이 호출하셨습니다! 매장으로 입장해 주세요.");
                break;
            case SEATED:
            case CANCEL:
            case NOSHOW:
                if (shouldNotify) { // 줄의 변동(상위 3명 이내)이 생겼을 때만 쏜다
                    checkAndNotifyThirdInLine(waiting.getStore().getId());
                }
                notificationService.closeConnection(waiting.getMember().getId());
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

        // 1. 미루기 전 내가 상위 3등 이내였는지 확인
        boolean shouldNotify = isTop3(waiting);

        // 2. 순서 맨 뒤로 미루기 (ticketTime 갱신)
        waiting.postpone();

        waitingRepository.flush();

        if (shouldNotify) {
            checkAndNotifyThirdInLine(waiting.getStore().getId());
        }
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

    private void checkAndNotifyThirdInLine(Long storeId) {
        PageRequest pageRequest = PageRequest.of(2, 1);
        Page<Waiting> thirdWaitingPage = waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                storeId, WaitingStatus.WAITING, pageRequest
        );

        if (thirdWaitingPage.hasContent()) {
            Waiting thirdWaiting = thirdWaitingPage.getContent().get(0);
            Long memberId = thirdWaiting.getMember().getId();

            String message = "📢 대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요.";
            notificationService.sendToClient(memberId, message);
        }
    }
}