package com.ondemandmonitoring.media.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ondemandmonitoring.media.config.MediaStorageEventSqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

class SqsMediaStorageEventConsumerTest {

    private SqsClient sqsClient;
    private S3StorageEventMessageProcessor processor;
    private SqsMediaStorageEventConsumer consumer;
    private Message message;

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        processor = mock(S3StorageEventMessageProcessor.class);
        MediaStorageEventSqsProperties properties = new MediaStorageEventSqsProperties();
        properties.setQueueUrl("https://sqs.example/media-events");
        consumer = new SqsMediaStorageEventConsumer(sqsClient, properties, processor);
        message = Message.builder()
                .messageId("message-1")
                .receiptHandle("receipt-1")
                .body("{}")
                .build();
    }

    @Test
    void deletesMessageOnlyAfterSuccessfulProcessing() {
        consumer.process(message);

        verify(processor).process("{}");
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void retainsMessageWhenProcessingFails() {
        doThrow(new IllegalStateException("temporary failure")).when(processor).process("{}");

        consumer.process(message);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
