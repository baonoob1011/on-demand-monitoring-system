package com.ondemandmonitoring.media.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class S3StorageEventMessageProcessorTest {

    private final IMediaUploadService service = org.mockito.Mockito.mock(IMediaUploadService.class);
    private final S3StorageEventMessageProcessor processor =
            new S3StorageEventMessageProcessor(new ObjectMapper(), service);

    @Test
    void ignoresS3TestEvent() {
        processor.process("""
                {"Service":"Amazon S3","Event":"s3:TestEvent","Bucket":"media-bucket"}
                """);

        verify(service, never()).processObjectCreated(org.mockito.ArgumentMatchers.any(
                StorageObjectCreatedEvent.class));
    }

    @Test
    void mapsAndDecodesObjectCreatedEvent() {
        processor.process("""
                {"Records":[{
                  "eventName":"ObjectCreated:Put",
                  "eventTime":"2026-09-13T11:55:05.070Z",
                  "s3":{
                    "bucket":{"name":"media-bucket"},
                    "object":{"key":"drone-media%2Fimage+one.jpg","size":123,"sequencer":"abc123"}
                  }
                }]}
                """);

        ArgumentCaptor<StorageObjectCreatedEvent> captor =
                ArgumentCaptor.forClass(StorageObjectCreatedEvent.class);
        verify(service).processObjectCreated(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().key())
                .isEqualTo("drone-media/image one.jpg");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sequencer()).isEqualTo("abc123");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(123L);
    }
}
