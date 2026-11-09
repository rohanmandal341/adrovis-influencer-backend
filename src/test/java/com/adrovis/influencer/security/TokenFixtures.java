package com.adrovis.influencer.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;

final class TokenFixtures {

    static final String ISSUER = "https://auth.example.com";
    static final String ACCOUNT = UUID.randomUUID().toString();
    static final KeyPair KEYS = keys();

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static AdrovisSecurityProperties properties() {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        return new AdrovisSecurityProperties(ISSUER, "adrovis-platform",
                new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)),
                URI.create(ISSUER + "/api/v1/auth/me"), null);
    }

    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject(ACCOUNT).audience("adrovis-platform")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("token_use", "access").claim("ver", 0).claim("roles", List.of("CREATOR"));
    }

    static String sign(JWTClaimsSet.Builder claims) {
        return sign(claims, KEYS, JWSAlgorithm.RS256);
    }

    static String sign(JWTClaimsSet.Builder claims, KeyPair keys, JWSAlgorithm algorithm) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT)
                    .keyID("production-key").build(), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
