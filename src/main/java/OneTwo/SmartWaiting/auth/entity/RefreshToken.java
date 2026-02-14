package OneTwo.SmartWaiting.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String value;

    public void updateValue(String token) {
        this.value = token;
    }

}
