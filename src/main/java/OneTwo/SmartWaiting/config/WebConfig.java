package OneTwo.SmartWaiting.config;

import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CORS 설정은 SecurityConfig의 corsConfigurationSource로 일원화했다
 * (Security 필터 체인에서 preflight까지 처리해야 인증 API가 브라우저에서 막히지 않기 때문).
 * 여기에 addCorsMappings를 다시 두면 CORS 헤더가 이중으로 붙으니 추가하지 말 것.
 */
@Configuration
public class WebConfig {

    @Bean
    public JtsModule jtsModule() {
        return new JtsModule();
    }
}
