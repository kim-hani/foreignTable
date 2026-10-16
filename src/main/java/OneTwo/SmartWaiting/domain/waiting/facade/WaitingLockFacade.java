package OneTwo.SmartWaiting.domain.waiting.facade;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.waiting.dto.requestDto.WaitingRegisterRequestDto;
import OneTwo.SmartWaiting.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingLockFacade {

    private final RedissonClient redissonClient;
    private final WaitingService waitingService;

    public Long registerWaiting(WaitingRegisterRequestDto requestDto, String email) {

        // 자물쇠 이름 -> 대기 등록은 가게마다 동시에 1명씩만 가능하도록 설정
        String lockKey = "waiting:store:" + requestDto.storeId();

        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(10, 5, TimeUnit.SECONDS); // 최대 10초 대기, 5초간 락 유지
            if (!isLocked) {
                log.error("락 획득 실패 , 너무 많은 요청이 몰렸습니다. Key: {}", lockKey);
                throw new BusinessException(ErrorCode.WAITING_LOCK_TIMEOUT);
            }

            return waitingService.registerWaiting(requestDto, email);
        } catch (InterruptedException e) {
            log.error("락 획득 중 예외 발생. Key: {}", e.getMessage());
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
}
