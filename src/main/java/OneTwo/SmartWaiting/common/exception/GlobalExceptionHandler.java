package OneTwo.SmartWaiting.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 직접 던진 비즈니스 예외 처리 (BusinessException)
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("BusinessException: [{}] {}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode(), request.getRequestURI());
    }

    // 2. DTO 유효성 검사 (@Valid) 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        // 첫 번째 에러 메시지만 추출 (예: "비밀번호를 입력해주세요.")
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("ValidationException: {}", errorMessage);

        // V001 이라는 커스텀 코드 부여
        return ErrorResponse.toResponseEntity(HttpStatus.BAD_REQUEST, "V001", errorMessage, request.getRequestURI());
    }

    // 3. 그 외에 예상하지 못한 모든 시스템 예외 처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("UnhandledException: ", e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI());
    }
}
