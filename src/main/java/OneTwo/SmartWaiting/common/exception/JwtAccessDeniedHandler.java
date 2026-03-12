package OneTwo.SmartWaiting.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // 1. 에러 코드 세팅 (403 권한 없음)
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        // 2. 응답 헤더 세팅
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getStatus().value());

        // 3. ErrorResponse 포장
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(request.getRequestURI())
                .build();

        // 4. JSON 문자열로 전송
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
