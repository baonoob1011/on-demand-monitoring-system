package com.ondemandmonitoring.media.messaging;

import com.ondemandmonitoring.media.config.MediaStorageEventSqsProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(prefix = "app.media.storage-event-sqs", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SqsMediaStorageEventConsumer implements SmartLifecycle {

    private final SqsClient sqsClient;
    private final MediaStorageEventSqsProperties properties;
    private final S3StorageEventMessageProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    @Override
    public void start() {
        validateConfiguration();
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofPlatform()
                .name("media-storage-event-sqs-consumer")
                .daemon(true)
                .start(this::poll);
        log.info("Media storage event SQS consumer started. queueUrl={}", properties.getQueueUrl());
    }

    private void poll() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(properties.getQueueUrl())
                .waitTimeSeconds(properties.getWaitTimeSeconds())
                .visibilityTimeout(properties.getVisibilityTimeoutSeconds())
                .maxNumberOfMessages(properties.getMaxMessages())
                .build();
        long retryDelayMillis = 1_000;
        while (running.get()) {
            try {
                for (Message message : sqsClient.receiveMessage(request).messages()) {
                    process(message);
                }
                retryDelayMillis = 1_000;
            } catch (RuntimeException exception) {
                if (running.get()) {
                    log.error("Cannot poll media storage event queue; retrying in {} ms",
                            retryDelayMillis, exception);
                    if (!pause(retryDelayMillis)) {
                        return;
                    }
                    retryDelayMillis = Math.min(retryDelayMillis * 2, 30_000);
                }
            }
        }
    }

    private boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void process(Message message) {
        try {
            processor.process(message.body());
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl())
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (RuntimeException exception) {
            log.error("Media storage event processing failed; message remains for retry. messageId={}",
                    message.messageId(), exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getQueueUrl())) {
            throw new IllegalStateException(
                    "MEDIA_STORAGE_EVENT_SQS_QUEUE_URL is required when the SQS consumer is enabled");
        }
        if (properties.getWaitTimeSeconds() < 0 || properties.getWaitTimeSeconds() > 20) {
            throw new IllegalStateException("SQS wait time must be between 0 and 20 seconds");
        }
        if (properties.getVisibilityTimeoutSeconds() <= 0) {
            throw new IllegalStateException("SQS visibility timeout must be positive");
        }
        if (properties.getMaxMessages() < 1 || properties.getMaxMessages() > 10) {
            throw new IllegalStateException("SQS max messages must be between 1 and 10");
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
        log.info("Media storage event SQS consumer stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
