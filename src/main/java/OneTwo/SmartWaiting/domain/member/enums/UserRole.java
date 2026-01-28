package OneTwo.SmartWaiting.domain.member.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum UserRole {

    ADMIN(Authority.ADMIN, "관리자"),
    USER(Authority.USER, "일반 사용자");

    private final String authority;
    private final String description;

    public static UserRole of(String role) {
        return Arrays.stream(UserRole.values())
                .filter(r -> r.name().equalsIgnoreCase(role) || r.authority.equalsIgnoreCase(role))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 권한입니다: " + role));
    }

    public static class Authority {
        public static final String ADMIN = "ROLE_ADMIN";
        public static final String USER = "ROLE_USER";
    }
}
