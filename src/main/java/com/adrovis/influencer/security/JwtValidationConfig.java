package com.adrovis.influencer.security;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(AdrovisSecurityProperties.class)
public class JwtValidationConfig {

    @Bean
    public JwtDecoder jwtDecoder(AdrovisSecurityProperties properties) {
        NimbusJwtDecoder local = signatureDecoder(properties);
        AccountStatusClient status = new AccountStatusClient(properties);
        return token -> {
            var jwt = local.decode(token);
            status.verify(jwt);
            return jwt;
        };
    }

    static NimbusJwtDecoder signatureDecoder(AdrovisSecurityProperties properties) {
        NimbusJwtDecoder local;
        try (InputStream input = properties.publicKeyLocation().getInputStream()) {
            var publicKey = RsaKeyConverters.x509().convert(input);
            if (publicKey == null || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("A trusted RSA public key of at least 2048 bits is required");
            }
            local = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the Adrovis public key; check its configured resource", exception);
        }
        local.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(properties.issuer()),
                new AccessTokenValidator(properties.audience())));
        return local;
    }
}
