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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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

    @PostMapping(value = "/api/background-refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> enableBackgroundRefresh() {
        final OAuth2AuthenticationToken token = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .map(OAuth2AuthenticationToken.class::cast)
                .orElse(null);

        if (token == null) {
            SecurityContextHolder.getContext().setAuthentication(null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

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

    @Scheduled(fixedRate = 1000L * 30L)
    public void refreshExpiredTokens() {
        boolean hasMore = true;

        while (hasMore) {
            OAuth2AuthorizedClient next;

            synchronized (this.monitor) {
                next = this.clientsToBeRefreshed.peek();

                if (next != null) {
                    // check if there is a new token (through login on the site)
                    final OAuth2AuthorizedClient client = this.oAuth2AuthorizedClientService.loadAuthorizedClient(next.getClientRegistration().getRegistrationId(), next.getPrincipalName());

                    if (client != null && !client.getRefreshToken().getTokenValue().equals(next.getRefreshToken().getTokenValue())) {
                        this.clientsToBeRefreshed.poll();
                        this.clientsToBeRefreshed.offer(client);
                        next = null;
                    } else if (Instant.now().isBefore(next.getAccessToken().getExpiresAt())) {
                        hasMore = false;
                    } else {
                        this.clientsToBeRefreshed.poll();
                    }
                } else {
                    hasMore = false;
                }
            }

            if (hasMore && next != null) {
                final String principalName = next.getPrincipalName();

                LOG.info("refreshing client={}", principalName);
                try {
                    next = refreshToken(next);
                } catch (Exception e) {
                    LOG.warn("refreshing client={} resulted in exception", principalName, e);
                }

                synchronized (this.monitor) {
                    if (next != null) {
                        this.clientsToBeRefreshed.offer(next);
                        LOG.info("refreshed client={} successfully", principalName);
                    } else {
                        this.addedPrincipals.remove(principalName);
                        LOG.warn("refreshing client={} returned null", principalName);
                    }
                }
            }
        }
    }

    public OAuth2AuthorizedClient refreshToken(OAuth2AuthorizedClient client) {
        return this.refreshTokenOAuth2AuthorizedClientProvider.authorize(
                OAuth2AuthorizationContext.withAuthorizedClient(client)
                        .principal(new NameAuthentication(client.getPrincipalName()))
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
