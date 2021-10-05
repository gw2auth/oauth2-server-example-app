package com.gw2auth.example.app.web;

import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

@RestController
public class AuthInfoController {

    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider;
    private final Clock clock;
    private final Duration clockSkew;

    public AuthInfoController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider, Clock clock, Duration clockSkew) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.refreshTokenOAuth2AuthorizedClientProvider = refreshTokenOAuth2AuthorizedClientProvider;
        this.clock = clock;
        this.clockSkew = clockSkew;
    }

    @Autowired
    public AuthInfoController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, RefreshTokenOAuth2AuthorizedClientProvider refreshTokenOAuth2AuthorizedClientProvider) {
        this(oAuth2AuthorizedClientService, refreshTokenOAuth2AuthorizedClientProvider, Clock.systemUTC(), Duration.ofSeconds(5L));
    }

    @GetMapping(value = "/api/authinfo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthInfo> getAuthInfo() {
        final OAuth2AuthenticationToken token = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .map(OAuth2AuthenticationToken.class::cast)
                .orElse(null);

        final AuthInfo authInfo;

        if (token == null || (authInfo = getAuthInfo(token)) == null) {
            SecurityContextHolder.getContext().setAuthentication(null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(authInfo);
    }

    private AuthInfo getAuthInfo(OAuth2AuthenticationToken token) {
        final OAuth2AuthorizedClient client = this.oAuth2AuthorizedClientService.loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), token.getName());
        final AuthInfo authInfo;

        if (client == null || (authInfo = getAuthInfo(token, client)) == null) {
            this.oAuth2AuthorizedClientService.removeAuthorizedClient(token.getAuthorizedClientRegistrationId(), token.getName());
            return null;
        }

        return authInfo;
    }

    private AuthInfo getAuthInfo(OAuth2AuthenticationToken token, OAuth2AuthorizedClient client) {
        if (hasTokenExpired(client.getAccessToken())) {
            final OAuth2RefreshToken refreshToken = client.getRefreshToken();
            if (refreshToken == null || hasTokenExpired(refreshToken)) {
                return null;
            }

            client = this.refreshTokenOAuth2AuthorizedClientProvider.authorize(
                    OAuth2AuthorizationContext.withAuthorizedClient(client)
                            .principal(token)
                            .build()
            );

            if (client == null) {
                return null;
            }

            this.oAuth2AuthorizedClientService.saveAuthorizedClient(client, token);
        }

        final String sub;
        final Set<String> gw2ApiPermissions;
        final Map<String, AuthInfo.Gw2ApiToken> gw2ApiTokens = new LinkedHashMap<>();

        try {
            final JWT jwt = JWTParser.parse(client.getAccessToken().getTokenValue());
            final JWTClaimsSet claims = jwt.getJWTClaimsSet();

            sub = claims.getSubject();
            gw2ApiPermissions = new LinkedHashSet<>(claims.getStringListClaim("gw2:permissions"));

            for (Map.Entry<String, Object> entry : claims.getJSONObjectClaim("gw2:tokens").entrySet()) {
                final String gw2AccountId = entry.getKey();
                final JSONObject value = (JSONObject) entry.getValue();
                final String name = value.getAsString("name");
                final String gw2ApiSubtoken = value.getAsString("token");
                final String error = value.getAsString("error");

                gw2ApiTokens.put(gw2AccountId, new AuthInfo.Gw2ApiToken(name, gw2ApiSubtoken, error));
            }
        } catch (ParseException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("jwt_parse_error"), e);
        }

        return new AuthInfo(sub, gw2ApiPermissions, gw2ApiTokens, client.getAccessToken().getExpiresAt());
    }

    private boolean hasTokenExpired(AbstractOAuth2Token token) {
        return token.getExpiresAt() != null && this.clock.instant().minus(this.clockSkew).isAfter(token.getExpiresAt());
    }
}
