package com.gw2auth.example.app.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@RestController
@EnableScheduling
public class BackgroundRefreshController {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRefreshController.class);

    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider;

    private final Object monitor;
    private final Set<String> addedPrincipals;
    private final Queue<OAuth2AuthorizedClient> clientsToBeRefreshed;

    @Autowired
    public BackgroundRefreshController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.refreshTokenOAuth2AuthorizedClientProvider = refreshTokenOAuth2AuthorizedClientProvider;

        this.monitor = new Object();
        this.addedPrincipals = new HashSet<>();
        this.clientsToBeRefreshed = new PriorityQueue<>(128, Comparator.comparing((v) -> v.getAccessToken().getExpiresAt()));
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
                if (Instant.now().isAfter(next.getAccessToken().getExpiresAt())) {
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
