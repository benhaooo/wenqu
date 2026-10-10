package com.wenqu.auth.service;

import com.wenqu.auth.config.AuthProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** 签发 Access Token（JWT，subject 为用户 id）。 */
@Component
class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    AccessTokenIssuer(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    Instant expiresAt(Instant issuedAt) {
        return issuedAt.plus(properties.accessTokenTtl());
    }

    String issue(long userId, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt(issuedAt))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
