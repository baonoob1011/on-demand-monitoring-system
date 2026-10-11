package com.ondemandmonitoring.finance.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omss.payment")
public record PaymentProperties(BigDecimal depositRate) {
    public PaymentProperties {
        if (depositRate == null) depositRate = new BigDecimal("0.30");
        if (depositRate.signum() <= 0 || depositRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("omss.payment.deposit-rate must be in (0, 1]");
        }
    }
}
