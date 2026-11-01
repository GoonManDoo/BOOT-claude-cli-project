package com.youngmin.board.common.config;

import com.youngmin.board.common.api.ApiSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

/**
 * 보안 설정을 두 개의 필터 체인으로 나눈다.
 * - API 체인 (/api/**): JSON 응답, HTTP Basic 인증, 세션 없음, CSRF 없음
 * - 화면 체인 (나머지): 폼 로그인, 세션, CSRF 보호
 * @Order가 작은 체인부터 securityMatcher를 비교해서, 먼저 맞는 체인 하나만 적용된다.
 */
@Configuration
public class SecurityConfig {

    /**
     * API 체인.
     * 화면처럼 세션 쿠키로 인증하면 다른 사이트가 쿠키를 이용해 요청을 위조할 수 있어서(CSRF) CSRF 토큰이 필요하다.
     * API는 요청마다 Authorization 헤더(HTTP Basic)로 인증하고 세션을 만들지 않으므로, 쿠키가 없어 CSRF를 끈다.
     * (연습용. 실제 서비스에서는 Basic 대신 JWT 같은 토큰을 쓰고 HTTPS가 필수다)
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(ApiSecurityHandlers.authenticationEntryPoint()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(ApiSecurityHandlers.authenticationEntryPoint()) // 401 JSON
                        .accessDeniedHandler(ApiSecurityHandlers.accessDeniedHandler()));         // 403 JSON
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                // 1) URL별 접근 권한 (위에서부터 순서대로 검사, 처음 맞는 규칙 적용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/members/join", "/login", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // "/posts/*"는 "/posts/new"에도 맞기 때문에, 로그인이 필요한 주소를 먼저 적는다.
                        .requestMatchers("/posts/new", "/posts/*/edit").authenticated()
                        // 글 목록과 상세, 첨부 이미지는 누구나 볼 수 있다 (GET만). 작성/수정/삭제(POST)는 아래에 걸린다.
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/*", "/post-images/*").permitAll()
                        .anyRequest().authenticated())

                // 2) 폼 로그인
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("loginId")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/posts")
                        .failureUrl("/login?error")
                        .permitAll())

                // 3) 로그아웃
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/posts?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))

                // 4) 로그인하지 않은 사용자가 로그인이 필요한 주소에 왔을 때의 처리 (위에서부터 먼저 맞는 것)
                //    - 좋아요: fetch(비동기)로 호출하므로 로그인 화면(HTML) 대신 401 상태 코드만 돌려준다.
                //    - 나머지: 로그인 화면으로 보낸다.
                //    (좋아요 규칙만 등록하면 Security가 그것을 모든 요청의 기본값으로 써 버리므로 둘 다 명시한다)
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/posts/*/like"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE));

        return http.build();
    }

    // 5) 비밀번호 암호화 방식
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
