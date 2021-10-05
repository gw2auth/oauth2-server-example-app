package com.gw2auth.example.app.configuration;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesRegistrationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OAuth2ClientConfiguration {

    @Bean
    public RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider() {
        return new RefreshTokenOAuth2AuthorizedClientProvider();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        // this is just a hacky way only relevant for this framework; the framework expects the scopes to be configured statically

        final List<ClientRegistration> registrations = new ArrayList<>(OAuth2ClientPropertiesRegistrationAdapter.getClientRegistrations(properties).values());
        final ClientRegistrationRepository parent = new InMemoryClientRegistrationRepository(registrations);

        return (registrationId) -> {
            final ClientRegistration clientRegistration = parent.findByRegistrationId(registrationId);
            if (clientRegistration == null) {
                return null;
            }

            final HttpServletRequest request = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                    .filter(ServletRequestAttributes.class::isInstance)
                    .map(ServletRequestAttributes.class::cast)
                    .map(ServletRequestAttributes::getRequest)
                    .orElse(null);

            final String query;
            if (request == null || (query = request.getQueryString()) == null) {
                return clientRegistration;
            }

            final List<String> scopeQueryParam = parseQuery(query).get(OAuth2ParameterNames.SCOPE);
            if (scopeQueryParam == null) {
                return clientRegistration;
            }

            final Set<String> scopes = scopeQueryParam.stream()
                    .flatMap((v) -> Arrays.stream(v.split(" ")))
                    .collect(Collectors.toSet());

            if (clientRegistration.getScopes().containsAll(scopes)) {
                return clientRegistration;
            }

            final Set<String> resultingScopes = new HashSet<>(scopes);
            resultingScopes.addAll(clientRegistration.getScopes());

            return ClientRegistration.withClientRegistration(clientRegistration)
                    .scope(resultingScopes)
                    .build();
        };
    }

    private static MultiValueMap<String, String> parseQuery(String query) {
        final MultiValueMap<String, String> result = new LinkedMultiValueMap<>();

        final String[] pairs = query.split("&");
        String[] pair;

        for (String _pair : pairs) {
            pair = _pair.split("=");

            if (pair.length >= 1) {
                final List<String> values = result.computeIfAbsent(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), (k) -> new ArrayList<>());

                if (pair.length >= 2) {
                    values.add(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
            }
        }

        return result;
    }
}
