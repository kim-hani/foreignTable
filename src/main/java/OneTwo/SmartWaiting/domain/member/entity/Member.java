package OneTwo.SmartWaiting.domain.member.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "member")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@SQLRestriction("is_deleted = false") // soft delete 적용
public class Member extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING) // [중요] Enum 이름을 문자열로 저장 (ROLE_USER)
    @Column(nullable = false)
    private UserRole role;

    @Column(unique = true)
    private String loginId;

    private String password;

    private String provider;

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void withdraw(){
        super.softDelete();

        this.email = "deleted_" + System.currentTimeMillis() + "_" + this.email;
        if(this.loginId != null){
            this.loginId = "deleted_" + System.currentTimeMillis() + "_" + this.loginId;
        }
    }
}
