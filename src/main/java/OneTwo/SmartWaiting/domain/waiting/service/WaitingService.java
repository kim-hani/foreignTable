package OneTwo.SmartWaiting.domain.waiting.service;

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
    public Long registerWaiting(WaitingRegisterRequestDto requestDto) {

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        Member member = memberRepository.findById(requestDto.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

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
                .build();

        return waitingRepository.save(waiting).getId();
    }

    @Transactional
    public void cancelWaiting(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("대기 정보를 찾을 수 없습니다."));

        waiting.changeStatus(WaitingStatus.CANCEL);
    }
    public List<WaitingResponse> getMyWaitings(Long memberId) {
        return waitingRepository.findAllByMemberId(memberId).stream()
                .map(WaitingResponse::from)
                .collect(Collectors.toList());
    }

    public List<WaitingResponse> getStoreWaitings(Long storeId, WaitingStatus status) {
        return waitingRepository.findAllByStoreIdAndStatus(storeId, status).stream()
                .map(WaitingResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void changeStatus(Long waitingId, WaitingChangeRequestDto request) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("대기 정보를 찾을 수 없습니다."));

        waiting.changeStatus(request.status());
    }
}
