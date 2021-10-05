package com.gw2auth.example.app.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record AuthInfo(@JsonProperty("sub") String sub,
                       @JsonProperty("gw2ApiPermissions") Set<String> gw2ApiPermissions,
                       @JsonProperty("gw2ApiTokens") Map<String, Gw2ApiToken> gw2ApiTokens,
                       @JsonProperty("expiresAt") Instant expiresAt) {

    public record Gw2ApiToken(@JsonProperty("name") String name,
                              @JsonProperty("token") String token,
                              @JsonProperty("error") String error) {

    }
}
