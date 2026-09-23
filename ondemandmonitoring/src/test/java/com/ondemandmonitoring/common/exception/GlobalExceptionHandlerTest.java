package com.ondemandmonitoring.common.exception;

import com.ondemandmonitoring.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/media/123/file");
    }

    @Test
    void testNormalJsonApiException() {
        Exception ex = new Exception("Normal error");
        when(response.isCommitted()).thenReturn(false);

        ResponseEntity<ApiResponse<Void>> res = handler.handleUnexpectedException(ex, request, response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.name(), res.getBody().getCode());
    }

    @Test
    void testConfirmedClientAbortException() {
        ClientAbortException ex = new ClientAbortException("Broken pipe");
        when(response.isCommitted()).thenReturn(false);

        ResponseEntity<ApiResponse<Void>> res = handler.handleUnexpectedException(ex, request, response);

        assertNull(res); // Should return null to avoid HttpMessageNotWritableException
    }

    @Test
    void testGenericIOExceptionNotClientDisconnect() {
        IOException ex = new IOException("Some other IO error");
        when(response.isCommitted()).thenReturn(false);

        ResponseEntity<ApiResponse<Void>> res = handler.handleUnexpectedException(ex, request, response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }

    @Test
    void testAlreadyCommittedResponse() {
        Exception ex = new Exception("Error during streaming");
        when(response.isCommitted()).thenReturn(true);

        ResponseEntity<ApiResponse<Void>> res = handler.handleUnexpectedException(ex, request, response);

        assertNull(res);
    }
}
