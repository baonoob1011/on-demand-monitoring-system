package com.ondemandmonitoring.media.messaging;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.media.sqs.enabled", havingValue = "true")
@Slf4j
public class SqsMediaEventConsumer implements SmartLifecycle {
    private final SqsClient sqs;
    private final S3EventMessageProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    @Value("${app.media.sqs.queue-url}")
    private String queueUrl;

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            worker = Thread.ofVirtual().name("media-sqs-consumer").start(this::poll);
        }
    }

    private void poll() {
        while (running.get()) {
            try {
                var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl).maxNumberOfMessages(5).waitTimeSeconds(20)
                        .visibilityTimeout(900).build());
                for (var message : response.messages()) {
                    try {
                        processor.process(message.body());
                        sqs.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
                    } catch (Exception error) {
                        log.error("Media event failed; SQS will retry messageId={}", message.messageId(), error);
                    }
                }
            } catch (RuntimeException error) {
                if (running.get()) {
                    log.warn("Media SQS polling failed; retrying", error);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
