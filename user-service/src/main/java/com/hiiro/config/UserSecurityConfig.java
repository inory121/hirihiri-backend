package com.hiiro.config;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.exp.UserException;
import com.hiiro.filter.JwtAuthenticationTokenFilter;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class UserSecurityConfig {

    @Resource
    private UserDetailsService userDetailsService;

    @Resource
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    /**
     * 密码BCrypt加密
     *
     * @return BCrypt加密后的密码
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests ->
                                authorizeRequests.requestMatchers("/user/login", "/user/register",
                                                "/doc.html",
                                                "/swagger-ui.html",
                                                "/swagger-ui*/**", "/swagger-resources/**",
                                                "/v3/**", "/webjars/**"
                                        )
//                                authorizeRequests.requestMatchers("**")
                                        .permitAll()
                                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
//                .formLogin(form ->form.loginPage("/user/login").permitAll())
//                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                // 从Authentication对象中获取用户名和身份凭证信息
                String username = authentication.getName();
                String password = authentication.getCredentials().toString();

                Optional<UserDetails> userDetailsOptional = Optional.ofNullable(userDetailsService.loadUserByUsername(username));

                if (userDetailsOptional.isEmpty() || !passwordEncoder().matches(password, userDetailsOptional.get().getPassword())) {
                    // 用户不存在或密码匹配失败抛出异常
                    throw new UserException(ResultCodeEnum.USERNAME_OR_PASSWORD_ERROR,"用户名或密码验证失败!");
//                    throw new BadCredentialsException("用户名或密码验证失败!");
                }

                UserDetails loginUser = userDetailsOptional.get();
                return new UsernamePasswordAuthenticationToken(loginUser, password, loginUser.getAuthorities());
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return authentication.equals(UsernamePasswordAuthenticationToken.class);
            }
        };
    }
}
