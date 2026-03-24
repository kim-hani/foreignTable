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
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

    private final WaitingRepository waitingRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(waitingRepository.existsByMemberIdAndStoreIdAndStatus(member.getId(), store.getId(), WaitingStatus.WAITING)) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_EXISTS);
        }

        long currentWaitingCount = waitingRepository.countByStoreIdAndStatus(store.getId(), WaitingStatus.WAITING);

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

        return waitingRepository.save(waiting).getId();
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

        waiting.changeStatus(WaitingStatus.CANCEL);
    }

    public List<WaitingResponse> getMyWaitings(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return waitingRepository.findAllByMemberId(member.getId()).stream()
                .map(waiting -> {
                    Long teamsAhead = 0L;
                    Integer expectedWaitMin = 0;

                    if (waiting.getStatus() == WaitingStatus.WAITING) {
                        teamsAhead = waitingRepository.countByStoreIdAndStatusAndTicketTimeLessThan(
                                waiting.getStore().getId(),
                                WaitingStatus.WAITING,
                                waiting.getCreatedAt()
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

        return waitingRepository.findAllByStoreIdAndStatus(storeId, status).stream()
                .map(waiting -> WaitingResponse.of(waiting, 0L, 0))
                .collect(Collectors.toList());
    }

    @Transactional
    public void changeStatus(Long waitingId, WaitingChangeRequestDto request, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()-> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(!waiting.getStore().getOwnerId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_STORE_OWNER);
        }
        waiting.changeStatus(request.status());
    }

    @Transactional
    public void postponeWaiting(Long waitingId,String email){
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(!waiting.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_YOUR_WAITING);
        }

        if(waiting.getStatus() != WaitingStatus.WAITING) {
            throw new BusinessException(ErrorCode.INVALID_WAITING_STATUS);
        }

        waiting.postpone();
    }
}
