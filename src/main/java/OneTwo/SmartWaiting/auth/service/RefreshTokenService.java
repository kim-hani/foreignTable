package OneTwo.SmartWaiting.auth.service;

import OneTwo.SmartWaiting.auth.entity.RefreshToken;
import OneTwo.SmartWaiting.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void saveOrUpdate(String key,String token){
        RefreshToken refreshToken = refreshTokenRepository.findByKey(key)
                .orElse(RefreshToken.builder()
                        .key(key)
                        .value(token)
                        .build());

        refreshToken.updateValue(token);
        refreshTokenRepository.save(refreshToken);
    }
}
