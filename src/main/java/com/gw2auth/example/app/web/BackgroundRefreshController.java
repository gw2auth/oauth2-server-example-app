package com.gw2auth.example.app.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@EnableScheduling
public class BackgroundRefreshController {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRefreshController.class);
    private static final Duration MAX_TOKEN_AGE = Duration.ofMinutes(5L);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5L);

    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider;

    private final Object monitor;
    private final Set<String> addedPrincipals;
    private final Queue<OAuth2AuthorizedClient> clientsToBeRefreshed;
    private final RestOperations revokeOldTokensRestTemplate;

    @Autowired
    public BackgroundRefreshController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.refreshTokenOAuth2AuthorizedClientProvider = refreshTokenOAuth2AuthorizedClientProvider;

        this.monitor = new Object();
        this.addedPrincipals = new HashSet<>();
        this.clientsToBeRefreshed = new PriorityQueue<>(128, Comparator.comparing((v) -> v.getAccessToken().getExpiresAt()));
        this.revokeOldTokensRestTemplate = new RestTemplateBuilder().build();
    }

    @GetMapping(value = "/api/background-refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean isBackgroundRefreshEnabled() {
        final String principalName = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .map(OAuth2AuthenticationToken.class::cast)
                .map(OAuth2AuthenticationToken::getName)
                .orElseThrow();

        synchronized (this.monitor) {
            return this.addedPrincipals.contains(principalName);
        }
    }

    @PostMapping(value = "/api/background-refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> enableBackgroundRefresh() {
        final OAuth2AuthenticationToken token = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .map(OAuth2AuthenticationToken.class::cast)
                .orElseThrow();

        final String principalName = token.getName();
        final OAuth2AuthorizedClient client = this.oAuth2AuthorizedClientService.loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), principalName);

        if (client == null) {
            SecurityContextHolder.getContext().setAuthentication(null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        synchronized (this.monitor) {
            if (this.addedPrincipals.add(principalName)) {
                this.clientsToBeRefreshed.offer(client);
            }
        }

        return ResponseEntity.ok(null);
    }

    @DeleteMapping(value = "/api/background-refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> disableBackgroundRefresh() {
        final String principalName = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .map(OAuth2AuthenticationToken.class::cast)
                .map(OAuth2AuthenticationToken::getName)
                .orElseThrow();

        synchronized (this.monitor) {
            if (this.addedPrincipals.remove(principalName)) {
                this.clientsToBeRefreshed.removeIf((v) -> v.getPrincipalName().equals(principalName));
            }
        }

        return ResponseEntity.ok(null);
    }

    @Scheduled(fixedRate = 1000L * 30L)
    public void refreshExpiredTokens() {
        boolean hasMore = true;

        while (hasMore) {
            OAuth2AuthorizedClient next = pollNextToBeRefreshedClient();

            if (next != null) {
                final Instant now = Instant.now();

                if (now.isAfter(next.getAccessToken().getExpiresAt().minus(CLOCK_SKEW))
                        || now.isAfter(next.getAccessToken().getIssuedAt().plus(MAX_TOKEN_AGE))) {

                    final String clientRegistrationId = next.getClientRegistration().getRegistrationId();
                    final String principalName = next.getPrincipalName();
                    final Authentication authentication = new NameAuthentication(principalName);

                    LOG.info("refreshing client={}", principalName);
                    try {
                        next = refreshClient(authentication, next);
                    } catch (ClientAuthorizationException e) {
                        final String errorCode = e.getError().getErrorCode();
                        if (!errorCode.equals(OAuth2ErrorCodes.SERVER_ERROR) && !errorCode.equals(OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE)) {
                            next = null;
                        }

                        LOG.warn("refreshing client={} resulted in exception", principalName, e);
                    } catch (Exception e) {
                        next = null;
                        LOG.warn("refreshing client={} resulted in exception", principalName, e);
                    }

                    if (next != null) {
                        synchronized (this.monitor) {
                            this.clientsToBeRefreshed.offer(next);
                        }

                        this.oAuth2AuthorizedClientService.saveAuthorizedClient(next, authentication);
                        LOG.info("refreshed client={} successfully", principalName);
                    } else {
                        synchronized (this.monitor) {
                            this.addedPrincipals.remove(principalName);
                        }

                        this.oAuth2AuthorizedClientService.removeAuthorizedClient(clientRegistrationId, principalName);
                        LOG.info("refreshing client={} returned null", principalName);
                    }
                } else {
                    synchronized (this.monitor) {
                        this.clientsToBeRefreshed.offer(next);
                    }

                    hasMore = false;
                }
            } else {
                hasMore = false;
            }
        }
    }

    private OAuth2AuthorizedClient pollNextToBeRefreshedClient() {
        OAuth2AuthorizedClient next = null;
        boolean finished = false;

        while (!finished) {
            synchronized (this.monitor) {
                next = this.clientsToBeRefreshed.poll();
            }

            if (next != null) {
                final String clientRegistrationId = next.getClientRegistration().getRegistrationId();
                final String principalName = next.getPrincipalName();
                final OAuth2AuthorizedClient savedClient = this.oAuth2AuthorizedClientService.loadAuthorizedClient(clientRegistrationId, principalName);

                if (savedClient != null && !next.getRefreshToken().getTokenValue().equals(savedClient.getRefreshToken().getTokenValue())) {
                    revokeTokensSafe(next);

                    synchronized (this.monitor) {
                        this.clientsToBeRefreshed.offer(savedClient);
                    }
                } else {
                    finished = true;
                }
            } else {
                finished = true;
            }
        }

        return next;
    }

    private OAuth2AuthorizedClient refreshClient(Authentication principal, OAuth2AuthorizedClient client) {
        return this.refreshTokenOAuth2AuthorizedClientProvider.authorize(
                OAuth2AuthorizationContext.withAuthorizedClient(client)
                        .principal(principal)
                        .build()
        );
    }

    private void revokeTokensSafe(OAuth2AuthorizedClient client) {
        final ClientRegistration clientRegistration = client.getClientRegistration();

        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(OAuth2ParameterNames.CLIENT_ID, clientRegistration.getClientId());
        params.add(OAuth2ParameterNames.CLIENT_SECRET, clientRegistration.getClientSecret());

        final List<OAuth2Token> tokens = new ArrayList<>(2);
        tokens.add(client.getAccessToken());

        if (client.getRefreshToken() != null) {
            tokens.add(client.getRefreshToken());
        }

        for (OAuth2Token token : tokens) {
            if (token instanceof OAuth2AccessToken) {
                params.set(OAuth2ParameterNames.TOKEN_TYPE_HINT, OAuth2ParameterNames.ACCESS_TOKEN);
            } else if (token instanceof OAuth2RefreshToken) {
                params.set(OAuth2ParameterNames.TOKEN_TYPE_HINT, OAuth2ParameterNames.REFRESH_TOKEN);
            } else {
                params.remove(OAuth2ParameterNames.TOKEN_TYPE_HINT);
            }

            params.set(OAuth2ParameterNames.TOKEN, token.getTokenValue());

            final ResponseEntity<Void> responseEntity;
            try {
                responseEntity = this.revokeOldTokensRestTemplate.exchange(
                        RequestEntity.post(clientRegistration.getProviderDetails().getIssuerUri() + "/oauth2/revoke")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(params),
                        Void.class
                );
            } catch (Exception e) {
                LOG.warn("failed to revoke token", e);
                continue;
            }

            LOG.info("revoked token; got status={}", responseEntity.getStatusCode().value());
        }
    }

    private static class NameAuthentication extends AbstractAuthenticationToken {

        private final String name;

        public NameAuthentication(String name) {
            super(null);
            this.name = name;
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return this.name;
        }
    }
}
