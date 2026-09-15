package com.ondemandmonitoring.media.messaging;

import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3StorageEventMessageProcessor {

    private final ObjectMapper objectMapper;
    private final IMediaUploadService mediaUploadService;

    public void process(String messageBody) {
        JsonNode root = objectMapper.readTree(messageBody);
        if ("s3:TestEvent".equals(text(root, "Event"))) {
            log.info("S3 test event received. bucket={}", text(root, "Bucket"));
            return;
        }

        JsonNode records = root.path("Records");
        if (!records.isArray()) {
            throw new IllegalArgumentException("Unsupported S3 notification payload: Records is missing");
        }
        for (JsonNode record : records) {
            String eventName = text(record, "eventName");
            if (eventName == null || !eventName.startsWith("ObjectCreated:")) {
                log.debug("Ignoring non-ObjectCreated S3 event. eventName={}", eventName);
                continue;
            }
            JsonNode s3 = record.path("s3");
            JsonNode object = s3.path("object");
            JsonNode responseElements = record.path("responseElements");
            String bucket = requiredText(s3.path("bucket"), "name");
            String key = URLDecoder.decode(requiredText(object, "key"), StandardCharsets.UTF_8);
            Long size = object.path("size").canConvertToLong() ? object.path("size").longValue() : null;
            String eventId = firstNonBlank(
                    text(responseElements, "x-amz-request-id"),
                    text(responseElements, "x-amz-id-2"));
            String eventTime = text(record, "eventTime");
            mediaUploadService.processObjectCreated(new StorageObjectCreatedEvent(
                    bucket,
                    key,
                    size,
                    eventId,
                    text(object, "versionId"),
                    text(object, "sequencer"),
                    eventName,
                    eventTime == null ? null : Instant.parse(eventTime)));
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 notification field is required: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
