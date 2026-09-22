package com.ondemandmonitoring.media.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.media.service.IMediaValidationService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3EventMessageProcessor {
    private final ObjectMapper mapper;
    private final IMediaValidationService validation;

    public void process(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        if (root.has("Message")) {
            root = mapper.readTree(root.path("Message").asText());
        }
        if ("s3:TestEvent".equals(root.path("Event").asText())) {
            return;
        }
        JsonNode records = root.path("Records");
        if (!records.isArray()) {
            throw new IllegalArgumentException("Expected S3 Records array");
        }
        for (JsonNode record : records) {
            String name = record.path("eventName").asText();
            if (!name.startsWith("ObjectCreated:")) {
                continue;
            }
            String bucket = record.path("s3").path("bucket").path("name").asText();
            String encodedKey = record.path("s3").path("object").path("key").asText();
            String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            String sequencer = record.path("s3").path("object").path("sequencer").asText();
            if (bucket.isBlank() || key.isBlank() || sequencer.isBlank()) {
                throw new IllegalArgumentException("Incomplete S3 ObjectCreated record");
            }
            validation.processObjectCreated(bucket, key, sequencer);
        }
    }
}
