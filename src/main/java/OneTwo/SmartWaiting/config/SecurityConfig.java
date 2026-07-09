package OneTwo.SmartWaiting.config;

import OneTwo.SmartWaiting.common.exception.JwtAccessDeniedHandler;
import OneTwo.SmartWaiting.common.exception.JwtAuthenticationEntryPoint;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    /**
     * 프론트엔드(로컬 개발) 출처의 교차 출처 요청 허용 설정.
     * localhost/127.0.0.1은 IPv6(::1) 이슈로 둘 다 등록한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화
                // CORS를 Security 필터 체인에 통합 — preflight(OPTIONS)를 인증 검사 전에 처리해야
                // Authorization 헤더가 붙는 보호된 API(예: /support/chat-rooms)의 요청이 브라우저에서 막히지 않는다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 사용 안 함 (JWT 사용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll() // 로그인 관련 페이지 허용
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // WebSocket 핸드셰이크(HTTP Upgrade)에는 Authorization 헤더가 없음
                        // — 인증은 STOMP CONNECT 프레임에서 수행 (StompAuthChannelInterceptor)
                        .requestMatchers("/ws/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/analytics").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET,"/api/v1/stores/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/stores").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stores/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/stores/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/stores/**").hasAnyRole("ADMIN","OWNER")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/waitings/*/status").hasRole("OWNER")

                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated() // 나머지는 다 로그인(JWT) 필요
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)) // 로그인 로직 연결
                        .successHandler(oAuth2SuccessHandler) // 로그인 성공 시 핸들러 연결
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint()) // 401 (토큰 없음/만료) 처리
                        .accessDeniedHandler(new JwtAccessDeniedHandler())           // 403 (권한 없음) 처리
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
