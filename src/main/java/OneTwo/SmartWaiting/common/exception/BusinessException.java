package OneTwo.SmartWaiting.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // RuntimeException의 메시지로도 세팅
        this.errorCode = errorCode;
    }
}
