package com.gw2auth.example.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauth2LoginCustomizer() {
        return (oauth2) -> oauth2.loginPage("/");
    }

    @Bean
    public Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer() {
        return (csrf) -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().antMatchers("/**/*.css", "/**/*.js", "/robots.txt");
    }

    @Bean
    public SecurityFilterChain httpSecurityFilterChain(HttpSecurity http,
                                                               Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauth2LoginCustomizer,
                                                               Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer) throws Exception {
        http
                .authorizeRequests((auth) -> {
                    auth
                            .antMatchers("/", "/privacy-policy", "/legal", "/assets/**", "/api/authinfo").permitAll()
                            .anyRequest().authenticated();
                })
                .csrf(csrfCustomizer)
                .headers((headers) -> {
                    headers
                            .frameOptions().deny()
                            .contentSecurityPolicy((csp) -> csp.policyDirectives(String.join("; ",
                                    "default-src 'self'",
                                    "connect-src 'self' https://api.guildwars2.com",
                                    "script-src 'self' 'unsafe-inline'",
                                    "style-src 'self' 'unsafe-inline'",
                                    "img-src 'self' data:"
                            )));
                })
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(oauth2LoginCustomizer)
                .logout((logout) -> logout.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));

        return http.build();
    }
}
