package OneTwo.SmartWaiting.common.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponse {
    private final LocalDateTime timestamp;
    private final int status;       // 예: 404
    private final String error;     // 예: NOT_FOUND
    private final String code;      // 예: M001
    private final String message;   // 예: 회원 정보를 찾을 수 없습니다.
    private final String path;      // 예: /api/v1/reviews/my

    // 1. ErrorCode를 받아서 ResponseEntity로 변환하는 메서드
    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String path) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(errorCode.getStatus().value())
                        .error(errorCode.getStatus().name())
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .path(path)
                        .build());
    }

    // 2. 직접 상태값과 메시지를 넣을 수 있는 메서드
    public static ResponseEntity<ErrorResponse> toResponseEntity(HttpStatus status, String code, String message, String path) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(status.value())
                        .error(status.name())
                        .code(code)
                        .message(message)
                        .path(path)
                        .build());
    }
}