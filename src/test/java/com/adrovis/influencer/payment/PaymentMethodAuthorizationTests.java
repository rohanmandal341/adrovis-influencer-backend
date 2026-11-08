package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.service.PaymentService;
import com.adrovis.influencer.payment.service.impl.PaymentServiceImpl;
import com.adrovis.influencer.security.CurrentAccount;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(PaymentMethodAuthorizationTests.Guards.class)
class PaymentMethodAuthorizationTests {

    @Configuration
    @EnableMethodSecurity
    static class Guards {
        @Bean
        PaymentService service() {
            return new PaymentServiceImpl(mock(PaymentTransactionRepository.class), mock(RazorpayClientWrapper.class),
                    new RazorpayProperties(), mock(CurrentAccount.class));
        }
    }

    @Autowired
    private PaymentService service;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directNonAdminServiceCallsCannotReadPlatformFinancialStatistics() {
        authenticate("BRAND");
        assertThatThrownBy(service::getAdminStats).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAllTransactions(null, org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theAdminServiceGuardAllowsLegitimateReporting() {
        authenticate("ADMIN");
        assertThatCode(service::getAdminStats).doesNotThrowAnyException();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account", "not-a-real-credential", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
