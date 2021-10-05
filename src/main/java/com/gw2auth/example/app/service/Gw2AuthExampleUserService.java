package com.gw2auth.example.app.service;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

@Service
public class Gw2AuthExampleUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        final Map<String, Object> claims;
        try {
            final JWT jwt = JWTParser.parse(userRequest.getAccessToken().getTokenValue());
            claims = jwt.getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("jwt_parse_error"), e);
        }

        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")), claims, "sub");
    }
}
