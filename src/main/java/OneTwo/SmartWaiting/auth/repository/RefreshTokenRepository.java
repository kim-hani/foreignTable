package OneTwo.SmartWaiting.auth.repository;

import OneTwo.SmartWaiting.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByKey(String key);
    Optional<RefreshToken> findByValue(String token);
}
