package OneTwo.SmartWaiting.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ===== 공통 (Common) =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    // ===== 인증 및 권한 (Auth & Security) =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "해당 기능을 사용할 권한이 없습니다."),
    INVALID_IDPASSWORD(HttpStatus.UNAUTHORIZED, "A003", "아이디 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않거나 만료된 토큰입니다."),
    INVALID_ADMIN_KEY(HttpStatus.UNAUTHORIZED, "A005", "잘못된 관리자 인증 키입니다."),

    // ===== 회원 (Member) =====
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원 정보를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "M002", "이미 가입된 이메일입니다."),
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "M003", "이미 존재하는 아이디입니다."),

    // ===== 가게 (Store) =====
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "식당을 찾을 수 없습니다."),
    UNAUTHORIZED_STORE_OWNER(HttpStatus.FORBIDDEN, "S002", "해당 가게에 대한 권한이 없습니다."),

    // ===== 웨이팅 (Waiting) =====
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "대기 정보를 찾을 수 없습니다."),
    WAITING_ALREADY_EXISTS(HttpStatus.CONFLICT, "W002", "이미 대기 중인 식당입니다."),
    POSTPONE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "W003", "웨이팅 미루기는 최대 2회까지만 가능합니다."),
    NOT_YOUR_WAITING(HttpStatus.FORBIDDEN, "W004", "본인의 대기 정보만 제어할 수 있습니다."),
    INVALID_WAITING_STATUS(HttpStatus.BAD_REQUEST, "W005", "대기 중인 상태에서만 가능한 작업입니다."),

    // ===== 리뷰 (Review) =====
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "R002", "이미 리뷰를 작성했습니다."),
    REVIEW_TIME_EXPIRED(HttpStatus.BAD_REQUEST, "R003", "리뷰는 식당 이용 후 48시간 이내에만 작성할 수 있습니다."),
    NOT_YOUR_REVIEW(HttpStatus.FORBIDDEN, "R004", "본인의 리뷰만 삭제할 수 있습니다."),
    REVIEW_UNAUTHORIZED_VISIT(HttpStatus.BAD_REQUEST, "R005", "식당을 이용한 고객만 리뷰를 작성할 수 있습니다."),

    // ===== 즐겨찾기 (Favorite) =====
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "즐겨찾기 내역이 존재하지 않습니다."),
    ALREADY_FAVORITE(HttpStatus.CONFLICT, "F002", "이미 즐겨찾기 한 식당입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}