package OneTwo.SmartWaiting.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException{

        // 1. 에러 코드 세팅(401 인증 실패)
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        // 2. 응답 헤더 세팅
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getStatus().value());

        // 3. 공통 에러
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .message(errorCode.getMessage())
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
