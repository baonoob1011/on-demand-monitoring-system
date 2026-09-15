package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyMediaUploadPolicyTest {

    @Test
    void multipartUploadIsDisabledByDefault() {
        assertThatThrownBy(() -> new LegacyMediaUploadPolicy(false).requireEnabled())
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void multipartUploadCanBeEnabledExplicitly() {
        new LegacyMediaUploadPolicy(true).requireEnabled();
    }
}
