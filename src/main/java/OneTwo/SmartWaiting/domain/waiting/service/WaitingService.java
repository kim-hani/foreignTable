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
    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if(waitingRepository.existsByMemberIdAndStoreIdAndStatus(member.getId(), store.getId(), WaitingStatus.WAITING)) {
            throw new IllegalArgumentException("이미 대기 중인 식당입니다.");
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
                .build();

        return waitingRepository.save(waiting).getId();
    }

    @Transactional
    public void cancelWaiting(Long waitingId, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("대기 정보를 찾을 수 없습니다."));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if (!waiting.getMember().getId().equals(member.getId())) {
            throw new IllegalArgumentException("본인의 대기 정보만 취소할 수 있습니다.");
        }

        waiting.changeStatus(WaitingStatus.CANCEL);
    }

    public List<WaitingResponse> getMyWaitings(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        return waitingRepository.findAllByMemberId(member.getId()).stream()
                .map(waiting -> {
                    Long teamsAhead = 0L;
                    Integer expectedWaitMin = 0;

                    if (waiting.getStatus() == WaitingStatus.WAITING) {
                        teamsAhead = waitingRepository.countByStoreIdAndStatusAndCreatedAtLessThan(
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
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        if (!store.getOwnerId().equals(member.getId())) {
            throw new IllegalArgumentException("본인의 식당 대기열만 조회할 수 있습니다.");
        }

        return waitingRepository.findAllByStoreIdAndStatus(storeId, status).stream()
                .map(waiting -> WaitingResponse.of(waiting, 0L, 0))
                .collect(Collectors.toList());
    }

    @Transactional
    public void changeStatus(Long waitingId, WaitingChangeRequestDto request, String email) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("대기 정보를 찾을 수 없습니다."));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()-> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if(!waiting.getStore().getOwnerId().equals(member.getId())) {
            throw new IllegalArgumentException("식당 주인만 대기 상태를 변경할 수 있습니다.");
        }
        waiting.changeStatus(request.status());
    }
}
