package com.ondemandmonitoring.service.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ServiceImageValidator {
    public static final long MAX_BYTES = 5 * 1024 * 1024;

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("Image must be non-empty and at most 5 MB");
        }
        byte[] header;
        try (var input = file.getInputStream()) {
            header = input.readNBytes(32);
        } catch (IOException exception) {
            throw invalid("Unable to read image");
        }
        String detected = null;
        if (header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            detected = "image/png";
        } else if (header.length >= 3 && (header[0] & 255) == 255
                && (header[1] & 255) == 216 && (header[2] & 255) == 255) {
            detected = "image/jpeg";
        } else if (header.length >= 16
                && "RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))
                && Arrays.asList("VP8 ", "VP8L", "VP8X").contains(
                        new String(header, 12, 4, StandardCharsets.US_ASCII))) {
            detected = "image/webp";
        }
        if (detected == null || !detected.equals(file.getContentType())) {
            throw invalid("Only JPG, PNG and WebP images with matching file signatures are supported");
        }
        return detected;
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}
