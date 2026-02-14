package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.domain.oauth.CustomOAuth2UserService;
import OneTwo.SmartWaiting.domain.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 사용 안 함 (JWT 사용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll() // 로그인 관련 페이지 허용
                        .requestMatchers("/api/v1/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/stores").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stores/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/stores/**").hasRole("OWNER")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/stores/**").hasAnyRole("ADMIN", "OWNER")

                        .anyRequest().authenticated() // 나머지는 다 로그인(JWT) 필요
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)) // 로그인 로직 연결
                        .successHandler(oAuth2SuccessHandler) // 로그인 성공 시 핸들러 연결
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
