package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaObjectVerificationService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaObjectVerificationServiceImpl implements IMediaObjectVerificationService {
    S3ObjectStorageService storage;

    @Override
    public String verify(MediaAsset captured, String key) {
        var object = storage.inspect(captured.getS3Bucket(), key);

        if (!captured.getFileSize().equals(object.contentLength())) {
            return "Object size differs from capture metadata";
        }

        if (!captured.getContentType().equalsIgnoreCase(object.contentType())) {
            return "Object content type differs from capture metadata";
        }

        if (!captured.getId().equals(object.metadata().get("media-id"))
                || !captured.getChecksumSha256().equalsIgnoreCase(object.metadata().get("sha256"))) {
            return "Object metadata differs from upload request";
        }

        try (InputStream stream = storage.open(captured.getS3Bucket(), key).inputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] signature = stream.readNBytes(12);
            digest.update(signature);
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            if (!validSignature(captured.getContentType(), signature)) {
                return "File signature differs from declared content type";
            }

            if (!HexFormat.of().formatHex(digest.digest())
                    .equalsIgnoreCase(captured.getChecksumSha256())) {
                return "SHA-256 checksum mismatch";
            }

            return null;

        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot inspect uploaded object", error);
        }
    }

    private boolean validSignature(String contentType, byte[] value) {
        return switch (contentType) {
            case "image/jpeg" -> value.length >= 3 && (value[0] & 0xff) == 0xff
                    && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
            case "image/png" -> value.length >= 8 && Arrays.equals(Arrays.copyOf(value, 8),
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "video/mp4" -> value.length >= 8 && value[4] == 'f' && value[5] == 't'
                    && value[6] == 'y' && value[7] == 'p';
            default -> false;
        };
    }

}

