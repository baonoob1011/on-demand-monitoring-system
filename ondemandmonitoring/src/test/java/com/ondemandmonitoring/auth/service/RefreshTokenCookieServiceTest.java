package com.ondemandmonitoring.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenCookieServiceTest {
    private RefreshTokenCookieService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenCookieService();
        ReflectionTestUtils.setField(service, "secure", true);
        ReflectionTestUtils.setField(service, "maxAgeSeconds", 3600);
        ReflectionTestUtils.setField(service, "sameSite", "Lax");
    }

    @Test
    void writeUsesSecureHttpOnlyCookie() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);

        service.write(response, "refresh-token", "cognito-user");

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), header.capture());
        assertTrue(header.getValue().contains("OMSS_REFRESH_TOKEN="));
        assertTrue(header.getValue().contains("HttpOnly"));
        assertTrue(header.getValue().contains("Secure"));
        assertTrue(header.getValue().contains("SameSite=Lax"));
        assertTrue(header.getValue().contains("Max-Age=3600"));
    }

    @Test
    void readReturnsRefreshTokenAndCognitoUsername() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("OMSS_REFRESH_TOKEN", "refresh-token::cognito-user")
        });

        var cookie = service.read(request).orElseThrow();

        assertEquals("refresh-token", cookie.token());
        assertEquals("cognito-user", cookie.username());
    }

    @Test
    void malformedCookieIsRejected() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("OMSS_REFRESH_TOKEN", "invalid")});

        assertTrue(service.read(request).isEmpty());
    }
}
